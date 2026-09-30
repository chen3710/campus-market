package com.campus.market.controller;

import com.campus.market.auth.LoginRequired;
import com.campus.market.common.Result;
import com.campus.market.dto.LoginRequest;
import com.campus.market.dto.RegisterRequest;
import com.campus.market.service.AuthService;
import com.campus.market.vo.LoginResponse;
import com.campus.market.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.ok(authService.register(request), "注册成功");
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request), "登录成功");
    }

    @GetMapping("/me")
    @LoginRequired
    public Result<UserVO> me() {
        return Result.ok(authService.currentUser());
    }
}
