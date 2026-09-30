package com.campus.market.dto;

import jakarta.validation.constraints.Size;

public record CancelOrderRequest(

        @Size(max = 200, message = "取消原因不能超过 200 个字")
        String reason
) {
}
