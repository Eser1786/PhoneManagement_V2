package com.phonestore.exception;

public class OutOfStockException extends Exception {

    private static final long serialVersionUID = 1L;

    private final int specId;

    public OutOfStockException(int specId) {
        super("Không đủ tồn kho cho phiên bản sản phẩm có spec_id = " + specId);
        this.specId = specId;
    }

    public int getSpecId() {
        return specId;
    }
}