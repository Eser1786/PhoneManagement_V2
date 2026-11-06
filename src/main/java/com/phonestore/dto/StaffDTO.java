package com.phonestore.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class StaffDTO {
    private int staffId;
    private int userId;
    private String position;
    private BigDecimal salary;
    private LocalDate hireDate;

    public StaffDTO() {}

    public StaffDTO(int staffId, int userId, String position, BigDecimal salary, LocalDate hireDate) {
        this.staffId = staffId;
        this.userId = userId;
        this.position = position;
        this.salary = salary;
        this.hireDate = hireDate;
    }

    public int getStaffId() { return staffId; }
    public void setStaffId(int staffId) { this.staffId = staffId; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }
    public BigDecimal getSalary() { return salary; }
    public void setSalary(BigDecimal salary) { this.salary = salary; }
    public LocalDate getHireDate() { return hireDate; }
    public void setHireDate(LocalDate hireDate) { this.hireDate = hireDate; }
}