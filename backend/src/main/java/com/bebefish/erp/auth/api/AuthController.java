package com.bebefish.erp.auth.api;

import com.bebefish.erp.auth.application.AuthService;
import com.bebefish.erp.auth.application.LoginResult;
import com.bebefish.erp.auth.application.PasswordLoginCommand;
import com.bebefish.erp.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login/password")
    public ApiResponse<LoginResult> loginWithPassword(@Valid @RequestBody PasswordLoginCommand command) {
        return ApiResponse.success(authService.loginWithPassword(command));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(authorization);
        return ApiResponse.success(null);
    }

    @GetMapping("/me")
    public ApiResponse<LoginResult> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success(authService.currentUser(authorization));
    }
}
