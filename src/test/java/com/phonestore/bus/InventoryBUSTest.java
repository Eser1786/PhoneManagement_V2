package com.phonestore.bus;

import com.phonestore.dao.InventoryDAO;
import com.phonestore.dto.InventoryDTO;
import com.phonestore.dto.ProductSpecDTO;
import com.phonestore.dto.UserDTO;
import com.phonestore.exception.BusinessException;
import com.phonestore.exception.OutOfStockException;
import com.phonestore.util.PermissionNames;
import com.phonestore.util.SessionManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Test InventoryBUS bằng DAO giả, không cần database. */
class InventoryBUSTest {

    private final Map<Integer, Integer> stockBySpec = new HashMap<>();
    private final List<InventoryDTO> savedLogs = new ArrayList<>();
    private InventoryBUS inventoryBUS;

    @BeforeEach
    void setUp() {
        stockBySpec.clear();
        savedLogs.clear();
        stockBySpec.put(1, 10); // specId = 1 đang có 10 máy

        InventoryDAO fakeInventoryDAO = new InventoryDAO() {
            @Override
            public boolean recordTransactionAndUpdateStock(InventoryDTO log) {
                Integer current = stockBySpec.get(log.getSpecId());
                if (current == null) return false;
                int delta = log.getQuantityIn() - log.getQuantityOut();
                if (current + delta < 0) return false;
                stockBySpec.put(log.getSpecId(), current + delta);
                log.setInventoryId(savedLogs.size() + 1);
                savedLogs.add(log);
                return true;
            }

            @Override
            public List<ProductSpecDTO> findLowStockSpecs(int threshold) {
                List<ProductSpecDTO> list = new ArrayList<>();
                stockBySpec.forEach((specId, stock) -> {
                    if (stock <= threshold) {
                        ProductSpecDTO s = new ProductSpecDTO();
                        s.setSpecId(specId);
                        s.setStockQuantity(stock);
                        list.add(s);
                    }
                });
                return list;
            }
        };

        inventoryBUS = new InventoryBUS(fakeInventoryDAO, new AuthBUS());
        loginWith(PermissionNames.MANAGE_INVENTORY);
    }

    @AfterEach
    void tearDown() {
        SessionManager.logout();
    }

    @Test
    void importStockIncreasesStockAndSavesLog() {
        InventoryDTO log = inventoryBUS.importStock(1, 5, "  Nhập lô mới  ");

        assertEquals(15, stockBySpec.get(1));
        assertEquals(5, log.getQuantityIn());
        assertEquals(0, log.getQuantityOut());
        assertEquals(InventoryBUS.TYPE_IMPORT, log.getTransactionType());
        assertEquals("Nhập lô mới", log.getNotes());
        assertEquals(1, savedLogs.size());
    }

    @Test
    void exportStockDecreasesStockAndSavesLog() {
        InventoryDTO log = inventoryBUS.exportStock(1, 4, null);

        assertEquals(6, stockBySpec.get(1));
        assertEquals(0, log.getQuantityIn());
        assertEquals(4, log.getQuantityOut());
        assertEquals(InventoryBUS.TYPE_EXPORT, log.getTransactionType());
        assertEquals("Xuất hàng khỏi kho", log.getNotes());
    }

    @Test
    void exportStockRejectsWhenNotEnoughStock() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> inventoryBUS.exportStock(1, 20, "Xuất quá số lượng"));
        assertTrue(e.getCause() instanceof OutOfStockException);
        assertEquals(10, stockBySpec.get(1), "Tồn kho phải giữ nguyên khi xuất thất bại");
        assertTrue(savedLogs.isEmpty());
    }

    @Test
    void rejectsNonPositiveQuantityOrInvalidSpec() {
        assertThrows(BusinessException.class, () -> inventoryBUS.importStock(1, 0, null));
        assertThrows(BusinessException.class, () -> inventoryBUS.exportStock(1, -2, null));
        assertThrows(BusinessException.class, () -> inventoryBUS.importStock(0, 5, null));
    }

    @Test
    void requiresManageInventoryPermission() {
        loginWith(PermissionNames.VIEW_PRODUCTS);
        assertThrows(BusinessException.class, () -> inventoryBUS.importStock(1, 5, null));
        assertThrows(BusinessException.class, () -> inventoryBUS.exportStock(1, 2, null));
    }

    @Test
    void lowStockReturnsSpecsAtOrBelowThreshold() {
        stockBySpec.put(2, 3);
        List<ProductSpecDTO> low = inventoryBUS.getLowStockSpecs(5);
        assertEquals(1, low.size());
        assertEquals(2, low.get(0).getSpecId());
    }

    private static void loginWith(String... permissions) {
        UserDTO u = new UserDTO();
        u.setUserId(2);
        u.setUsername("manager");
        SessionManager.login(u, List.of(permissions));
    }
}