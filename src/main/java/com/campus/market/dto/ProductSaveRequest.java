package com.campus.market.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record ProductSaveRequest(

        @NotBlank(message = "商品标题不能为空")
        @Size(max = 50, message = "商品标题不能超过 50 个字")
        String title,

        @Size(max = 2000, message = "商品描述不能超过 2000 个字")
        String description,

        @NotNull(message = "售价不能为空")
        @DecimalMin(value = "0.01", message = "售价必须大于 0")
        BigDecimal price,

        @DecimalMin(value = "0.00", message = "原价不能为负数")
        BigDecimal originalPrice,

        @NotNull(message = "请选择商品分类")
        Long categoryId,

        @Size(max = 16, message = "成色描述过长")
        String conditionLevel,

        String cover,

        List<String> images
) {
}
