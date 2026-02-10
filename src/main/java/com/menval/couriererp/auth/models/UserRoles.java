package com.menval.couriererp.auth.models;

public enum UserRoles {
    /** Tenant-level: regular user within a tenant */
    WAREHOUSE,
    CASHIER,
    DIRECTOR,
    /** Tenant-level: admin within a tenant */
    ADMIN,
    /** System-level: platform administrator (your company); can create/manage tenants */
    SUPER_ADMIN;

    public static String getRole(UserRoles role) {
        return role == null ? null : role.name();
    }

    public static UserRoles getRole(String role) {
        if (role == null || role.isBlank()) throw new IllegalArgumentException("Role is required");
        try {
            return UserRoles.valueOf(role.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Unexpected value: " + role);
        }
    }
}