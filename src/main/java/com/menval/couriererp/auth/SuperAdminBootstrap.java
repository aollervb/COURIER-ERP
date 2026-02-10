package com.menval.couriererp.auth;

import com.menval.couriererp.auth.models.BaseUser;
import com.menval.couriererp.auth.models.UserRoles;
import com.menval.couriererp.auth.repository.UserRepository;
import com.menval.couriererp.tenant.TenantBootstrap;
import com.menval.couriererp.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Creates the first super-admin user if none exists (e.g. first run).
 * Default: superadmin@example.com / changeme (override with courier.superadmin.initial-password).
 */
@Component
@Order(200)
public class SuperAdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrap.class);
    private static final String SUPER_ADMIN_EMAIL = "superadmin@example.com";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${courier.superadmin.initial-password:changeme}")
    private String initialPassword;

    public SuperAdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        TenantContext.setTenantId(TenantBootstrap.SYSTEM_TENANT_ID);
        try {
            if (userRepository.findByEmail(SUPER_ADMIN_EMAIL).isPresent()) {
                log.debug("Super-admin {} already exists, skipping bootstrap.", SUPER_ADMIN_EMAIL);
                return;
            }

            BaseUser user = BaseUser.builder()
                    .email(SUPER_ADMIN_EMAIL)
                    .firstName("Super")
                    .lastName("Admin")
                    .password(passwordEncoder.encode(initialPassword))
                    .roles(Set.of(UserRoles.SUPER_ADMIN))
                    .accountNonExpired(true)
                    .credentialsNonExpired(true)
                    .accountNonLocked(true)
                    .enabled(true)
                    .build();
            user.setTenantId(TenantBootstrap.SYSTEM_TENANT_ID);
            userRepository.save(user);
            log.info("Created initial super-admin user: {}", SUPER_ADMIN_EMAIL);
        } finally {
            TenantContext.clear();
        }
    }
}
