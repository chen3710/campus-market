package com.campus.market.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderVO {

    private Long id;
    private String orderNo;

    private Long productId;
    private String productTitle;
    private String productCover;

    private Long buyerId;
    private String buyerNickname;

    private Long sellerId;
    private String sellerNickname;

    private BigDecimal amount;
    private String status;
    private String statusDesc;

    private String remark;
    private String contact;
    private String cancelReason;

    private LocalDateTime createTime;
    private LocalDateTime confirmTime;
    private LocalDateTime finishTime;
    private LocalDateTime cancelTime;

    /** 当前登录用户在这笔订单里的身份：BUYER / SELLER */
    private String viewerRole;

    /** 当前状态下该用户可执行的动作，由状态机推导，前端据此决定显示哪些按钮 */
    private List<Action> actions;

    @Data
    public static class Action {
        private String key;
        private String label;
        private boolean primary;

        public Action(String key, String label, boolean primary) {
            this.key = key;
            this.label = label;
            this.primary = primary;
        }
    }
}
