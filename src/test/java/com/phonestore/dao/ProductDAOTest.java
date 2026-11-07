package com.phonestore.dao;

import com.phonestore.config.DBConnection;
import com.phonestore.dto.ProductDTO;
import com.phonestore.dto.ProductFilter;
import com.phonestore.dto.ProductListItemDTO;
import com.phonestore.dto.ProductSpecDTO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Test tích hợp cho ProductDAO và ProductSpecDAO: chạy trên database thật
 * (cần 'docker compose up -d' và dữ liệu mẫu 02_insert.sql).
 * Nếu không kết nối được database, các test được bỏ qua (không báo lỗi).
 *
 * Mỗi test tự tạo sản phẩm có tên duy nhất rồi tự xóa, nên không làm bẩn dữ liệu mẫu.
 */
class ProductDAOTest {

    private static final int APPLE = 1;      // brands.brand_id theo dữ liệu mẫu
    private static final int SAMSUNG = 2;
    private static final int FLAGSHIP = 1;   // categories.category_id theo dữ liệu mẫu
    private static final int MID_RANGE = 2;

    private final ProductDAO productDAO = new ProductDAO();
    private final ProductSpecDAO specDAO = new ProductSpecDAO();

    private String uniqueName;
    private int productId;

    @BeforeAll
    static void requireDatabase() {
        boolean ok;
        try (Connection ignored = DBConnection.getConnection()) {
            ok = true;
        } catch (Exception e) {
            ok = false;
        }
        assumeTrue(ok, "Không kết nối được database, bỏ qua test DAO");
    }

    @AfterAll
    static void closePool() {
        DBConnection.close();
    }

    @BeforeEach
    void setUp() {
        uniqueName = "ZZ_TEST_" + UUID.randomUUID().toString().substring(0, 8);
        productId = -1;
    }

    @AfterEach
    void cleanUp() throws Exception {
        if (productId > 0) {
            productDAO.delete(productId);   // ProductSpecs bị xóa theo (CASCADE)
        }
    }

    @Test
    void productCrud() throws Exception {
        productId = productDAO.insert(newProduct(APPLE, FLAGSHIP));
        assertTrue(productId > 0);

        ProductDTO found = productDAO.findById(productId);
        assertNotNull(found);
        assertEquals(uniqueName, found.getName());
        assertEquals(APPLE, found.getBrandId());
        assertEquals(FLAGSHIP, found.getCategoryId());

        found.setName(uniqueName + "_edit");
        found.setBrandId(SAMSUNG);
        assertTrue(productDAO.update(found));
        ProductDTO updated = productDAO.findById(productId);
        assertEquals(uniqueName + "_edit", updated.getName());
        assertEquals(SAMSUNG, updated.getBrandId());

        assertTrue(productDAO.findAll().stream().anyMatch(p -> p.getProductId() == productId));

        assertTrue(productDAO.delete(productId));
        assertNull(productDAO.findById(productId));
        assertFalse(productDAO.delete(productId));   // xóa lần 2: không còn dòng nào
        productId = -1;
    }

    @Test
    void specCrud() throws Exception {
        productId = productDAO.insert(newProduct(APPLE, FLAGSHIP));
        int specId = specDAO.insert(newSpec(productId, "12345000.00", 7, "Đang bán", "128GB"));
        assertTrue(specId > 0);

        ProductSpecDTO s = specDAO.findById(specId);
        assertNotNull(s);
        assertEquals(productId, s.getProductId());
        assertEquals(0, new BigDecimal("12345000").compareTo(s.getPrice()));
        assertEquals(7, s.getStockQuantity());
        assertEquals("128GB", s.getStorage());

        s.setPrice(new BigDecimal("11111000.00"));
        s.setStatus("Hết hàng");
        s.setStockQuantity(0);
        assertTrue(specDAO.update(s));
        ProductSpecDTO updated = specDAO.findById(specId);
        assertEquals(0, new BigDecimal("11111000").compareTo(updated.getPrice()));
        assertEquals("Hết hàng", updated.getStatus());

        specDAO.insert(newSpec(productId, "15000000.00", 3, "Đang bán", "256GB"));
        List<ProductSpecDTO> specs = specDAO.findByProductId(productId);
        assertEquals(2, specs.size());
        assertTrue(specs.get(0).getPrice().compareTo(specs.get(1).getPrice()) <= 0, "phải sắp theo giá tăng dần");

        assertTrue(specDAO.delete(specId));
        assertNull(specDAO.findById(specId));
    }

    @Test
    void searchJoinsNamesAndFilters() throws Exception {
        productId = productDAO.insert(newProduct(APPLE, FLAGSHIP));
        specDAO.insert(newSpec(productId, "12000000.00", 5, "Đang bán", "128GB"));
        specDAO.insert(newSpec(productId, "30000000.00", 0, "Hết hàng", "512GB"));

        // keyword duy nhất => chỉ ra đúng 2 dòng của sản phẩm test; kiểm tra phần join
        ProductFilter byName = new ProductFilter();
        byName.setKeyword(uniqueName);
        List<ProductListItemDTO> all = productDAO.search(byName);
        assertEquals(2, all.size());
        ProductListItemDTO first = all.get(0);
        assertEquals(uniqueName, first.getProductName());
        assertEquals("Apple", first.getBrandName());
        assertEquals("Flagship", first.getCategoryName());
        assertEquals(0, new BigDecimal("12000000").compareTo(first.getPrice()), "sắp theo giá tăng dần");

        // lọc hãng
        ProductFilter f = new ProductFilter();
        f.setKeyword(uniqueName);
        f.setBrandId(APPLE);
        assertEquals(2, productDAO.search(f).size());
        f.setBrandId(SAMSUNG);
        assertEquals(0, productDAO.search(f).size());

        // lọc loại
        f = new ProductFilter();
        f.setKeyword(uniqueName);
        f.setCategoryId(FLAGSHIP);
        assertEquals(2, productDAO.search(f).size());
        f.setCategoryId(MID_RANGE);
        assertEquals(0, productDAO.search(f).size());

        // lọc khoảng giá (cả hai đầu đều tính)
        f = new ProductFilter();
        f.setKeyword(uniqueName);
        f.setMinPrice(new BigDecimal("12000000"));
        f.setMaxPrice(new BigDecimal("12000000"));
        assertEquals(1, productDAO.search(f).size());
        f.setMinPrice(new BigDecimal("13000000"));
        f.setMaxPrice(null);
        assertEquals(1, productDAO.search(f).size());
        assertEquals("512GB", productDAO.search(f).get(0).getStorage());

        // lọc trạng thái
        f = new ProductFilter();
        f.setKeyword(uniqueName);
        f.setStatus("Hết hàng");
        List<ProductListItemDTO> soldOut = productDAO.search(f);
        assertEquals(1, soldOut.size());
        assertEquals("Hết hàng", soldOut.get(0).getStatus());

        // kết hợp nhiều điều kiện
        f = new ProductFilter();
        f.setKeyword(uniqueName);
        f.setBrandId(APPLE);
        f.setCategoryId(FLAGSHIP);
        f.setStatus("Đang bán");
        f.setMaxPrice(new BigDecimal("20000000"));
        assertEquals(1, productDAO.search(f).size());
    }

    @Test
    void searchWithoutFilterReturnsSeedData() throws Exception {
        List<ProductListItemDTO> noFilter = productDAO.search(new ProductFilter());
        List<ProductListItemDTO> nullFilter = productDAO.search(null);
        assertEquals(noFilter.size(), nullFilter.size());

        // dữ liệu mẫu 02_insert.sql có ít nhất 19 phiên bản, trong đó 4 phiên bản Apple
        assertTrue(noFilter.size() >= 19);
        ProductFilter apple = new ProductFilter();
        apple.setBrandId(APPLE);
        assertTrue(productDAO.search(apple).size() >= 4);
    }

    @Test
    void keywordWithLikeWildcardsIsTreatedLiterally() throws Exception {
        productId = productDAO.insert(newProduct(APPLE, FLAGSHIP));
        specDAO.insert(newSpec(productId, "1000000.00", 1, "Đang bán", "64GB"));

        ProductFilter f = new ProductFilter();
        f.setKeyword("%");          // nếu không thoát ký tự, sẽ khớp mọi sản phẩm
        assertEquals(0, productDAO.search(f).size());
    }


    private ProductDTO newProduct(int brandId, int categoryId) {
        ProductDTO p = new ProductDTO();
        p.setName(uniqueName);
        p.setBrandId(brandId);
        p.setCategoryId(categoryId);
        p.setDescription("Sản phẩm dùng cho test, sẽ bị xóa");
        return p;
    }

    private ProductSpecDTO newSpec(int productId, String price, int stock, String status, String storage) {
        ProductSpecDTO s = new ProductSpecDTO();
        s.setProductId(productId);
        s.setPrice(new BigDecimal(price));
        s.setCostPrice(new BigDecimal(price).multiply(new BigDecimal("0.9")));
        s.setStockQuantity(stock);
        s.setScreenSize("6.1 inch");
        s.setProcessor("Chip test");
        s.setResolution("2556x1179");
        s.setRam("8GB");
        s.setStorage(storage);
        s.setBattery("3000 mAh");
        s.setCameraMain("48MP");
        s.setCameraFront("12MP");
        s.setWeight("180 g");
        s.setOs("Android");
        s.setColor("Đen");
        s.setWarrantyPeriod("12 tháng");
        s.setStatus(status);
        return s;
    }
}