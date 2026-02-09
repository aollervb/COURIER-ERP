package com.menval.couriererp.auth.services;

import com.menval.couriererp.auth.dto.SignUpRequest;

public interface AuthService {
    void signUp(SignUpRequest request);
}
