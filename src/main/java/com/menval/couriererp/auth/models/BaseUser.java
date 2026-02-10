package com.menval.couriererp.auth.models;

import com.menval.couriererp.modules.common.models.TenantScopedBaseModel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uq_users_tenant_email", columnNames = {"tenant_id", "email"})
}, indexes = {
        @Index(name = "idx_users_email", columnList = "email"),
        @Index(name = "idx_users_tenant", columnList = "tenant_id")
})
@Builder
@AllArgsConstructor
public class BaseUser extends TenantScopedBaseModel implements UserDetails {

    @Column(nullable = false)
    private String email;
    @Column(nullable = false)
    private String firstName;
    @Column(nullable = false)
    private String lastName;
    @Column(nullable = false)
    private String password;
    private boolean enabled;
    private boolean accountNonExpired;
    private boolean accountNonLocked;
    private boolean credentialsNonExpired;
    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            uniqueConstraints = @UniqueConstraint(name = "uq_user_roles", columnNames = {"user_id", "role"})
    )
    @Column(name = "role", nullable = false)
    private Set<UserRoles> roles = new HashSet<>();

    public BaseUser() {}

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.name()))
                .toList();
    }

    /** Expose tenant ID for security/tenant context. */
    public String getUserTenantId() {
        return getTenantId();
    }

    public boolean isSuperAdmin() {
        return roles != null && roles.contains(UserRoles.SUPER_ADMIN);
    }

    public boolean isTenantAdmin() {
        return roles != null && roles.contains(UserRoles.ADMIN);
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Set<UserRoles> getRoles() { return roles; }
    public void setRoles(Set<UserRoles> roles) { this.roles = roles; }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return accountNonExpired;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void eraseCredentials(){
        this.password=null;
    }
}
