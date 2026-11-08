package com.phonestore.bus;

import com.phonestore.dao.InventoryDAO;
import com.phonestore.dto.InventoryDTO;
import com.phonestore.dto.ProductSpecDTO;
import com.phonestore.exception.BusinessException;
import com.phonestore.exception.OutOfStockException;
import com.phonestore.util.PermissionNames;

import java.util.List;

public class InventoryBUS {

    public static final String TYPE_IMPORT = "Nhập kho";
    public static final String TYPE_EXPORT = "Xuất kho";
    public static final int DEFAULT_LOW_STOCK_THRESHOLD = 5;

    private final InventoryDAO inventoryDAO;
    private final AuthBUS authBUS;

    public InventoryBUS() {
        this(new InventoryDAO(), new AuthBUS());
    }

    public InventoryBUS(InventoryDAO inventoryDAO, AuthBUS authBUS) {
        this.inventoryDAO = inventoryDAO;
        this.authBUS = authBUS;
    }

    public List<InventoryDTO> getAllInventoryLogs() {
        authBUS.requirePermission(PermissionNames.MANAGE_INVENTORY);
        return inventoryDAO.findAll();
    }

    public List<InventoryDTO> getLogsBySpecId(int specId) {
        authBUS.requirePermission(PermissionNames.MANAGE_INVENTORY);
        if (specId <= 0) {
            throw new BusinessException("Mã phiên bản sản phẩm không hợp lệ");
        }
        return inventoryDAO.findBySpecId(specId);
    }

    public InventoryDTO importStock(int specId, int quantity, String notes) {
        authBUS.requirePermission(PermissionNames.MANAGE_INVENTORY);
        if (specId <= 0) {
            throw new BusinessException("Mã phiên bản sản phẩm không hợp lệ");
        }
        if (quantity <= 0) {
            throw new BusinessException("Số lượng phải lớn hơn 0");
        }

        InventoryDTO log = new InventoryDTO();
        log.setSpecId(specId);
        log.setQuantityIn(quantity);
        log.setQuantityOut(0);
        log.setTransactionType(TYPE_IMPORT);
        log.setNotes(notes == null || notes.isBlank() ? "Nhập hàng vào kho" : notes.trim());

        if (!inventoryDAO.recordTransactionAndUpdateStock(log)) {
            throw new BusinessException("Không thể nhập kho, vui lòng kiểm tra lại phiên bản sản phẩm");
        }
        return log;
    }

    public InventoryDTO exportStock(int specId, int quantity, String notes) {
        authBUS.requirePermission(PermissionNames.MANAGE_INVENTORY);
        if (specId <= 0) {
            throw new BusinessException("Mã phiên bản sản phẩm không hợp lệ");
        }
        if (quantity <= 0) {
            throw new BusinessException("Số lượng phải lớn hơn 0");
        }

        InventoryDTO log = new InventoryDTO();
        log.setSpecId(specId);
        log.setQuantityIn(0);
        log.setQuantityOut(quantity);
        log.setTransactionType(TYPE_EXPORT);
        log.setNotes(notes == null || notes.isBlank() ? "Xuất hàng khỏi kho" : notes.trim());

        if (!inventoryDAO.recordTransactionAndUpdateStock(log)) {
            OutOfStockException cause = new OutOfStockException(specId);
            throw new BusinessException("Số lượng tồn kho không đủ để xuất kho", cause);
        }
        return log;
    }

    public List<ProductSpecDTO> getLowStockSpecs(int threshold) {
        if (threshold < 0) {
            throw new BusinessException("Ngưỡng cảnh báo tồn kho không được âm");
        }
        return inventoryDAO.findLowStockSpecs(threshold);
    }

    public List<ProductSpecDTO> getLowStockSpecs() {
        return inventoryDAO.findLowStockSpecs(DEFAULT_LOW_STOCK_THRESHOLD);
    }
}