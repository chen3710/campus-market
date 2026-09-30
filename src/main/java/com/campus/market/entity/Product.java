package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("product")
public class Product {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String description;

    private BigDecimal price;

    private BigDecimal originalPrice;

    private String cover;

    private Long categoryId;

    private Long sellerId;

    /** 成色描述，如「九成新」 */
    private String conditionLevel;

    /** 见 {@link com.campus.market.enums.ProductStatus} */
    private String status;

    private Integer viewCount;

    private Integer favoriteCount;

    /**
     * 乐观锁版本号。配合 OptimisticLockerInnerInterceptor，
     * updateById 时会自动追加 WHERE version = 旧值，冲突则影响行数为 0。
     */
    @Version
    private Integer version;

    @TableLogic
    @JsonIgnore
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
