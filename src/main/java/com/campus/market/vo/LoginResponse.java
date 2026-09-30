package com.campus.market.vo;

import lombok.Data;

@Data
public class LoginResponse {

    private String token;
    private long expiresIn;
    private UserVO user;

    public LoginResponse(String token, long expiresIn, UserVO user) {
        this.token = token;
        this.expiresIn = expiresIn;
        this.user = user;
    }
}
