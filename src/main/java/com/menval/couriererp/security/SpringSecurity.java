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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
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
                                            "/auth/signup",
                                            "/auth/signup/**",
                                            "/css/**", "/js/**", "/images/**",
                                            "/error"
                                    ).permitAll()
                                    .requestMatchers("/api/public/**", "/api/integration/**").authenticated()
                                    .requestMatchers("/api/admin/**", "/admin/**").hasRole("SUPER_ADMIN")
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
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/integration/**", "/api/public/**")
                );

        return http.build();
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
