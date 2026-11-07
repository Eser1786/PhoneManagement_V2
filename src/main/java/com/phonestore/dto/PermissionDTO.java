package com.phonestore.dto;

public class PermissionDTO {
    private int permissionId;
    private String permissionName;
    private String description;

    public PermissionDTO() {}

    public PermissionDTO(int permissionId, String permissionName, String description) {
        this.permissionId = permissionId;
        this.permissionName = permissionName;
        this.description = description;
    }

    public int getPermissionId() { return permissionId; }
    public void setPermissionId(int permissionId) { this.permissionId = permissionId; }
    public String getPermissionName() { return permissionName; }
    public void setPermissionName(String permissionName) { this.permissionName = permissionName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}