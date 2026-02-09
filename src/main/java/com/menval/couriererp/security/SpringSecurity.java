package com.menval.couriererp.security;

import com.menval.couriererp.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SpringSecurity {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(reqConfigurer -> {
                            reqConfigurer
                                    .requestMatchers(
                                            "/templates/auth/login",
                                            "/templates/auth/signup",
                                            "/templates/auth/signup/**",
                                            "/css/**", "/js/**", "/images/**",
                                            "/error",
                                            "/api/public/**"
                                    ).permitAll()
                                    .anyRequest()
                                    .authenticated();
                        }
                )
                .sessionManagement(sessionManagement -> sessionManagement
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                )

                .formLogin(form -> form
                        .loginPage("/templates/auth/login")
                        // IMPORTANT: processing endpoint != login page
                        .loginProcessingUrl("/templates/auth/login-process")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/templates/auth/login?error=true")
                        .permitAll()
                )

                .logout(logout -> logout
                        .logoutUrl("/templates/auth/logout")
                        .logoutSuccessUrl("/templates/auth/login?logout=true")
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
