package com.phonestore.dto;

import java.math.BigDecimal;

/**
 * Một điểm của biểu đồ doanh thu: một ngày ("2026-11-12") hoặc một tháng ("2026-11").
 * Chỉ tính đơn chưa hủy. Lợi nhuận = doanh thu - giá vốn, giá vốn tính theo ProductSpecs.cost_price hiện tại.
 */
public class RevenuePointDTO {

    private String period;
    private int orderCount;
    private BigDecimal revenue;
    private BigDecimal cost;
    private BigDecimal profit;

    public RevenuePointDTO() {
    }

    public RevenuePointDTO(String period, int orderCount, BigDecimal revenue, BigDecimal cost) {
        this.period = period;
        this.orderCount = orderCount;
        this.revenue = revenue;
        this.cost = cost;
        this.profit = revenue.subtract(cost);
    }

    /** Điểm không có đơn nào, dùng để điền khoảng trống cho biểu đồ liền mạch. */
    public static RevenuePointDTO empty(String period) {
        return new RevenuePointDTO(period, 0, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    public int getOrderCount() { return orderCount; }
    public void setOrderCount(int orderCount) { this.orderCount = orderCount; }

    public BigDecimal getRevenue() { return revenue; }
    public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }

    public BigDecimal getCost() { return cost; }
    public void setCost(BigDecimal cost) { this.cost = cost; }

    public BigDecimal getProfit() { return profit; }
    public void setProfit(BigDecimal profit) { this.profit = profit; }
}
