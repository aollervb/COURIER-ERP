package com.menval.couriererp.auth.services;

import com.menval.couriererp.auth.dto.SignUpRequest;
import com.menval.couriererp.auth.models.BaseUser;
import com.menval.couriererp.auth.models.UserRoles;
import com.menval.couriererp.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public void signUp(SignUpRequest signUpRequest) {
        userRepository.save(BaseUser.builder()
                .email(signUpRequest.getEmail())
                .firstName(signUpRequest.getFirstName())
                .lastName(signUpRequest.getLastName())
                .password(passwordEncoder.encode(signUpRequest.getPassword()))
                .roles(Collections.singleton(UserRoles.DIRECTOR))
                .accountNonExpired(true)
                .credentialsNonExpired(true)
                .accountNonLocked(true)
                .enabled(true)
                .build());
    }
}
