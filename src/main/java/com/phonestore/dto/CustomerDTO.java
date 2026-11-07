package com.phonestore.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CustomerDTO {
    private int customerId;
    private Integer userId;
    private String address;
    private String city;
    private String membershipLevel;
    private BigDecimal totalSpent;
    private LocalDateTime createdAt;

    public CustomerDTO() {}

    public CustomerDTO(int customerId, Integer userId, String address, String city,
                       String membershipLevel, BigDecimal totalSpent, LocalDateTime createdAt) {
        this.customerId = customerId;
        this.userId = userId;
        this.address = address;
        this.city = city;
        this.membershipLevel = membershipLevel;
        this.totalSpent = totalSpent;
        this.createdAt = createdAt;
    }

    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getMembershipLevel() { return membershipLevel; }
    public void setMembershipLevel(String membershipLevel) { this.membershipLevel = membershipLevel; }
    public BigDecimal getTotalSpent() { return totalSpent; }
    public void setTotalSpent(BigDecimal totalSpent) { this.totalSpent = totalSpent; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}