package com.menval.couriererp.security;

import com.menval.couriererp.tenant.TenantContext;
import com.menval.couriererp.tenant.services.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

/**
 * For /api/public/** and /api/integration/**, authenticates via API key.
 * Reads X-API-Key or Authorization: Bearer &lt;key&gt;, validates, sets TenantContext and SecurityContext.
 * The scoped tenant is set only from the tenant that owns the API key (from the lookup result), not from any other source.
 * Returns 401 if key is missing or invalid.
 */
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";
    public static final String BEARER_PREFIX = "Bearer ";

    private final ApiKeyService apiKeyService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String rawKey = extractApiKey(request);
        if (rawKey == null || rawKey.isBlank()) {
            sendUnauthorized(response, "Missing API key. Send X-API-Key header or Authorization: Bearer <key>");
            return;
        }

        // Lookup must run without tenant context so we find the key by hash only; tenant is set from the key owner below.
        TenantContext.clear();
        Optional<String> tenantIdOpt = apiKeyService.validateAndGetTenantId(rawKey);
        if (tenantIdOpt.isEmpty()) {
            sendUnauthorized(response, "Invalid or expired API key");
            return;
        }

        String tenantId = tenantIdOpt.get(); // tenant that owns the key
        TenantContext.setTenantId(tenantId);
        SecurityContextHolder.getContext().setAuthentication(
                new ApiKeyAuthenticationToken(tenantId)
        );
        filterChain.doFilter(request, response);
    }

    private static String extractApiKey(HttpServletRequest request) {
        String header = request.getHeader(API_KEY_HEADER);
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith(BEARER_PREFIX)) {
            return auth.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }

    private static void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + message.replace("\"", "\\\"") + "\"}");
    }

    /**
     * Authentication token for API key–authenticated requests. Principal is the tenant ID.
     */
    public static final class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {
        private final String tenantId;

        public ApiKeyAuthenticationToken(String tenantId) {
            super(Collections.singletonList(new SimpleGrantedAuthority("ROLE_API")));
            this.tenantId = tenantId;
            setAuthenticated(true);
        }

        @Override
        public Object getCredentials() {
            return null;
        }

        @Override
        public Object getPrincipal() {
            return tenantId;
        }
    }
}
