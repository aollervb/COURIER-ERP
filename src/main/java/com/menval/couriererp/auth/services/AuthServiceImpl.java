package com.menval.couriererp.auth.services;

import com.menval.couriererp.auth.models.BaseUser;
import com.menval.couriererp.auth.models.UserRoles;
import com.menval.couriererp.auth.repository.UserRepository;
import com.menval.couriererp.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void createUserForTenant(String tenantId, String email, String firstName, String lastName, String password, UserRoles role) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant ID is required");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        TenantContext.setTenantId(tenantId);
        userRepository.save(BaseUser.builder()
                .email(email.trim())
                .firstName(firstName != null ? firstName.trim() : "")
                .lastName(lastName != null ? lastName.trim() : "")
                .password(passwordEncoder.encode(password))
                .roles(Set.of(role != null ? role : UserRoles.ADMIN))
                .accountNonExpired(true)
                .credentialsNonExpired(true)
                .accountNonLocked(true)
                .enabled(true)
                .build());
    }
}
