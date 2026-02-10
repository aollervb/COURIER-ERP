package com.menval.couriererp.tenant;

import com.menval.couriererp.tenant.entities.TenantEntity;
import com.menval.couriererp.tenant.repositories.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Sets the current tenant for the request.
 * <p>
 * <b>Only the login form</b> sets the tenant: when the request is POST to /auth/login-process,
 * the "tenant" form field is read and validated; that tenant is set for the request. For all other
 * (non-API) requests, tenant is left as default until {@link TenantAccessFilter} sets it from the
 * authenticated user.
 * <p>
 * API paths are set by {@link com.menval.couriererp.security.ApiKeyAuthenticationFilter} (API key).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TenantContextFilter extends OncePerRequestFilter {

    private static final String LOGIN_PROCESSING_PATH = "/auth/login-process";

    /** Form parameter name for tenant ID on the login form. */
    public static final String TENANT_PARAM = "tenant";

    private final TenantRepository tenantRepository;

    public TenantContextFilter(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String path = request.getRequestURI();
            if (path.startsWith("/api/public/") || path.startsWith("/api/integration/")) {
                // API: tenant set by ApiKeyAuthenticationFilter
            } else if ("POST".equalsIgnoreCase(request.getMethod()) && LOGIN_PROCESSING_PATH.equals(path)) {
                String tenantId = resolveTenantFromLoginForm(request);
                TenantContext.setTenantId(tenantId);
            } else {
                TenantContext.setTenantId(null);
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * Resolve tenant only from the login form "tenant" field. No domain, header, or query param.
     */
    private String resolveTenantFromLoginForm(HttpServletRequest request) {
        String tenantParam = request.getParameter(TENANT_PARAM);
        if (tenantParam == null || tenantParam.isBlank()) {
            return null;
        }
        String tid = tenantParam.trim().toLowerCase();
        if (TenantBootstrap.SYSTEM_TENANT_ID.equals(tid)) {
            return tid;
        }
        var tenant = tenantRepository.findByTenantId(tid);
        if (tenant.filter(TenantEntity::isActive).filter(t -> !t.isExpired()).isPresent()) {
            return tenant.get().getTenantId();
        }
        return null;
    }
}
