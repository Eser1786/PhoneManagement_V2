package com.phonestore.dto;

import java.time.LocalDateTime;

public class InventoryDTO {
    private int inventoryId;
    private Integer specId;
    private int quantityIn;
    private int quantityOut;
    private String transactionType;
    private LocalDateTime transactionDate;
    private String notes;

    public InventoryDTO() {}

    public InventoryDTO(int inventoryId, Integer specId, int quantityIn, int quantityOut,
                        String transactionType, LocalDateTime transactionDate, String notes) {
        this.inventoryId = inventoryId;
        this.specId = specId;
        this.quantityIn = quantityIn;
        this.quantityOut = quantityOut;
        this.transactionType = transactionType;
        this.transactionDate = transactionDate;
        this.notes = notes;
    }

    public int getInventoryId() { return inventoryId; }
    public void setInventoryId(int inventoryId) { this.inventoryId = inventoryId; }
    public Integer getSpecId() { return specId; }
    public void setSpecId(Integer specId) { this.specId = specId; }
    public int getQuantityIn() { return quantityIn; }
    public void setQuantityIn(int quantityIn) { this.quantityIn = quantityIn; }
    public int getQuantityOut() { return quantityOut; }
    public void setQuantityOut(int quantityOut) { this.quantityOut = quantityOut; }
    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }
    public LocalDateTime getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDateTime transactionDate) { this.transactionDate = transactionDate; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}