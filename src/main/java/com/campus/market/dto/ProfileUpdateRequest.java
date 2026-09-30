package com.campus.market.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(

        @Size(max = 16, message = "昵称不能超过 16 个字")
        String nickname,

        String avatar,

        @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
        String phone,

        @Size(max = 64, message = "校区名称过长")
        String campus
) {
}
