package com.campus.market.service;

import com.campus.market.dto.LoginRequest;
import com.campus.market.dto.RegisterRequest;
import com.campus.market.vo.LoginResponse;
import com.campus.market.vo.UserVO;

public interface AuthService {

    LoginResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    UserVO currentUser();
}
