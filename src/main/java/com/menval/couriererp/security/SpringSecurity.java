package com.menval.couriererp.security;

import com.menval.couriererp.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity()
@RequiredArgsConstructor
public class SpringSecurity {

    /** URL the login form must POST to. There is no controller for this path — the Security filter handles it. */
    public static final String LOGIN_PROCESSING_URL = "/auth/login-process";

    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(reqConfigurer -> {
                            reqConfigurer
                                    .requestMatchers(
                                            "/auth/login",
                                            "/css/**", "/js/**", "/images/**",
                                            "/error"
                                    ).permitAll()
                                    .requestMatchers("/api/**").access(apiKeyOnly())
                                    .requestMatchers("/admin/**").hasRole("SUPER_ADMIN")
                                    .requestMatchers("/settings/**").hasAnyRole("DIRECTOR", "ADMIN")
                                    .anyRequest()
                                    .authenticated();
                        }
                )
                .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .sessionManagement(sessionManagement -> sessionManagement
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )

                .formLogin(form -> form
                        .loginPage("/auth/login")
                        .loginProcessingUrl(LOGIN_PROCESSING_URL)
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/auth/login?error=true")
                        .permitAll()
                )

                .logout(logout -> logout
                        .logoutUrl("/auth/logout")
                        .logoutSuccessUrl("/auth/login?logout=true")
                )
                // Login/logout: no CSRF so they work when session isn't established or for simple logout link
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/**", LOGIN_PROCESSING_URL, "/auth/logout")
                );

        return http.build();
    }

    private static AuthorizationManager<RequestAuthorizationContext> apiKeyOnly() {
        return (authenticationSupplier, context) -> {
            var auth = authenticationSupplier.get();
            return new AuthorizationDecision(
                    auth != null && auth.isAuthenticated() && auth instanceof ApiKeyAuthenticationFilter.ApiKeyAuthenticationToken
            );
        };
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository) {
        return username -> userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}


