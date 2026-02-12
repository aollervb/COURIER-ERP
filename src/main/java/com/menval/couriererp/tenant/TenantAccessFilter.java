package com.menval.couriererp.tenant;

import com.menval.couriererp.auth.models.BaseUser;
import com.menval.couriererp.tenant.entities.TenantEntity;
import com.menval.couriererp.tenant.exceptions.TenantAccessDeniedException;
import com.menval.couriererp.tenant.exceptions.TenantExpiredException;
import com.menval.couriererp.tenant.exceptions.TenantSuspendedException;
import com.menval.couriererp.tenant.repositories.TenantRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * For authenticated requests, sets tenant from the authenticated user (not from header).
 * Validates that the tenant exists and can access (active, not expired, not suspended).
 * Runs after the Security filter chain so authentication is available.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 200)
public class TenantAccessFilter extends OncePerRequestFilter {

    private final TenantRepository tenantRepository;

    public TenantAccessFilter(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (isPublicEndpoint(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof BaseUser baseUser) {
            try {
                String tenantId = baseUser.getUserTenantId();

                // SUPER_ADMIN on admin views use "system" tenant (no TenantEntity required)
                if (baseUser.isSuperAdmin() && path.startsWith("/admin/")) {
                    TenantContext.setTenantId("system");
                    filterChain.doFilter(request, response);
                    return;
                }

                // SUPER_ADMIN with tenant "system" — no validation; but deny tenant-operational paths
                if (baseUser.isSuperAdmin() && "system".equals(tenantId)) {
                    if (path.startsWith("/packages/")) {
                        response.sendRedirect(request.getContextPath() + "/admin");
                        return;
                    }
                    TenantContext.setTenantId(tenantId);
                    filterChain.doFilter(request, response);
                    return;
                }

                // Regular users and tenant admins — must have valid tenant
                if (tenantId == null || tenantId.isBlank()) {
                    sendForbidden(response, "User has no tenant assigned");
                    return;
                }

                validateAndSetTenant(tenantId);
            } catch (TenantAccessDeniedException | TenantSuspendedException | TenantExpiredException e) {
                sendForbidden(response, e.getMessage());
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private static void sendForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + message.replace("\"", "\\\"") + "\"}");
    }

    private void validateAndSetTenant(String tenantId) {
        TenantEntity tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new TenantAccessDeniedException("Tenant not found: " + tenantId));

        if (!tenant.isActive()) {
            throw new TenantAccessDeniedException("Tenant is inactive: " + tenantId);
        }
        if (tenant.isExpired()) {
            throw new TenantExpiredException(tenantId);
        }
        if (!tenant.canAccess()) {
            throw new TenantSuspendedException(tenantId);
        }

        TenantContext.setTenantId(tenantId);
    }

    private static boolean isPublicEndpoint(String path) {
        return path.startsWith("/auth/")
                || path.startsWith("/api/public/")
                || path.startsWith("/css/")
                || path.startsWith("/js/")
                || path.startsWith("/images/")
                || path.equals("/error");
    }
}
