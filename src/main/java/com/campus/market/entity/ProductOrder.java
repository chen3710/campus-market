package com.campus.market.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("product_order")
public class ProductOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long productId;

    /** 下单时的商品标题快照，卖家之后改标题也不影响历史订单 */
    private String productTitle;

    private String productCover;

    private Long buyerId;

    private Long sellerId;

    private BigDecimal amount;

    /** 见 {@link com.campus.market.enums.OrderStatus} */
    private String status;

    private String remark;

    private String contact;

    private String cancelReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    private LocalDateTime confirmTime;

    private LocalDateTime finishTime;

    private LocalDateTime cancelTime;
}
