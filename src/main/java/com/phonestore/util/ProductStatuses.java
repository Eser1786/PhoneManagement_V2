package com.phonestore.util;

import java.util.List;

/** Các trạng thái của một phiên bản sản phẩm (ProductSpecs.status). Dùng chung cho DAO, BUS và GUI. */
public final class ProductStatuses {

    public static final String ON_SALE = "Đang bán";
    public static final String OUT_OF_STOCK = "Hết hàng";
    public static final String DISCONTINUED = "Ngừng kinh doanh";

    /** Dùng để đổ vào ComboBox trạng thái. */
    public static final List<String> ALL = List.of(ON_SALE, OUT_OF_STOCK, DISCONTINUED);

    private ProductStatuses() {
    }
}
