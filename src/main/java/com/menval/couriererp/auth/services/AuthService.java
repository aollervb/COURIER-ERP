package com.menval.couriererp.auth.services;

import com.menval.couriererp.auth.models.UserRoles;

public interface AuthService {

    /**
     * Create a user in a specific tenant (e.g. first tenant admin). SUPER_ADMIN only.
     * Sets tenant context so the new user gets the given tenant_id.
     */
    void createUserForTenant(String tenantId, String email, String firstName, String lastName, String password, UserRoles role);
}
