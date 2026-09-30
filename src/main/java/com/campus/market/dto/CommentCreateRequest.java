package com.campus.market.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentCreateRequest(

        @NotBlank(message = "留言内容不能为空")
        @Size(max = 500, message = "留言不能超过 500 个字")
        String content,

        Long parentId
) {
}
