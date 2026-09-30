package com.campus.market.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrderCreateRequest(

        @NotNull(message = "商品 ID 不能为空")
        Long productId,

        @Size(max = 200, message = "留言不能超过 200 个字")
        String remark,

        @Size(max = 64, message = "联系方式过长")
        String contact
) {
}
