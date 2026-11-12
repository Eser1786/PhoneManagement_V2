package com.phonestore.dto;

import java.math.BigDecimal;

/** Các con số tổng quan cho thẻ trên màn hình Dashboard. Doanh thu chỉ tính đơn chưa hủy. */
public class DashboardSummaryDTO {

    private BigDecimal revenueToday = BigDecimal.ZERO;
    private int ordersToday;
    private BigDecimal revenueThisMonth = BigDecimal.ZERO;
    private int pendingOrders;       // đơn đang ở trạng thái "Chờ xử lý"
    private int totalCustomers;
    private int productsOnSale;      // số phiên bản đang bán
    private int lowStockCount;       // số phiên bản sắp hết hàng (còn <= ngưỡng, chưa ngừng kinh doanh)

    public DashboardSummaryDTO() {
    }

    public BigDecimal getRevenueToday() { return revenueToday; }
    public void setRevenueToday(BigDecimal revenueToday) { this.revenueToday = revenueToday; }

    public int getOrdersToday() { return ordersToday; }
    public void setOrdersToday(int ordersToday) { this.ordersToday = ordersToday; }

    public BigDecimal getRevenueThisMonth() { return revenueThisMonth; }
    public void setRevenueThisMonth(BigDecimal revenueThisMonth) { this.revenueThisMonth = revenueThisMonth; }

    public int getPendingOrders() { return pendingOrders; }
    public void setPendingOrders(int pendingOrders) { this.pendingOrders = pendingOrders; }

    public int getTotalCustomers() { return totalCustomers; }
    public void setTotalCustomers(int totalCustomers) { this.totalCustomers = totalCustomers; }

    public int getProductsOnSale() { return productsOnSale; }
    public void setProductsOnSale(int productsOnSale) { this.productsOnSale = productsOnSale; }

    public int getLowStockCount() { return lowStockCount; }
    public void setLowStockCount(int lowStockCount) { this.lowStockCount = lowStockCount; }
}
