package com.phonestore.dto;

import java.util.Objects;

/**
 * Một lựa chọn (id + tên) để đổ vào ComboBox, ví dụ danh sách hãng hoặc loại sản phẩm.
 * toString() trả về tên nên ComboBox hiển thị đúng chữ mà không cần cấu hình thêm.
 */
public class LookupItem {

    private final int id;
    private final String name;

    public LookupItem(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LookupItem && ((LookupItem) o).id == id && Objects.equals(((LookupItem) o).name, name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }
}
