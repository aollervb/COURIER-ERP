package com.menval.couriererp.auth.models;

public enum UserRoles {
    WAREHOUSE,
    CASHIER,
    DIRECTOR;


    public static String getRole(UserRoles role) {
        return switch (role) {
            case DIRECTOR -> "DIRECTOR";
            case WAREHOUSE -> "WAREHOUSE";
            case CASHIER -> "CASHIER";
        };
    }

    public static UserRoles getRole(String role) {
        return switch (role) {
            case "DIRECTOR" -> DIRECTOR;
            case "WAREHOUSE" -> WAREHOUSE;
            case "CASHIER" -> CASHIER;
            default -> throw new IllegalStateException("Unexpected value: " + role);
        };
    }
}