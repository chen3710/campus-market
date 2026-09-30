-- =============================================================================
-- 校园二手交易平台 - 数据库初始化脚本
-- MySQL 8.0+
-- 执行方式: mysql -u root -p < sql/init.sql
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE DATABASE IF NOT EXISTS campus_market
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE campus_market;

DROP TABLE IF EXISTS credit_log;
DROP TABLE IF EXISTS favorite;
DROP TABLE IF EXISTS comment;
DROP TABLE IF EXISTS product_order;
DROP TABLE IF EXISTS product_image;
DROP TABLE IF EXISTS product;
DROP TABLE IF EXISTS category;
DROP TABLE IF EXISTS `user`;

-- -----------------------------------------------------------------------------
-- 1. 用户表
-- -----------------------------------------------------------------------------
CREATE TABLE `user` (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username     VARCHAR(32)     NOT NULL                COMMENT '登录账号',
    password     VARCHAR(100)    NOT NULL                COMMENT 'BCrypt 加密后的密码',
    nickname     VARCHAR(32)     NOT NULL                COMMENT '昵称',
    avatar       VARCHAR(255)             DEFAULT NULL   COMMENT '头像地址',
    phone        VARCHAR(20)              DEFAULT NULL   COMMENT '联系方式',
    campus       VARCHAR(64)              DEFAULT NULL   COMMENT '所在校区',
    credit_score INT             NOT NULL DEFAULT 100    COMMENT '信用分, 初始 100',
    role         VARCHAR(16)     NOT NULL DEFAULT 'USER' COMMENT '角色: USER / ADMIN',
    status       TINYINT         NOT NULL DEFAULT 1      COMMENT '状态: 1 正常, 0 封禁',
    deleted      TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0 未删, 1 已删',
    create_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    KEY idx_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- -----------------------------------------------------------------------------
-- 2. 商品分类表
-- -----------------------------------------------------------------------------
CREATE TABLE category (
    id       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    name     VARCHAR(32)     NOT NULL                COMMENT '分类名称',
    icon     VARCHAR(16)              DEFAULT NULL   COMMENT '图标字符',
    sort_no  INT             NOT NULL DEFAULT 0      COMMENT '排序值, 越小越靠前',
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品分类表';

-- -----------------------------------------------------------------------------
-- 3. 商品表
--    version 字段用于 CAS 乐观锁, 保证同一商品不会被重复下单
-- -----------------------------------------------------------------------------
CREATE TABLE product (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    title           VARCHAR(100)    NOT NULL                COMMENT '商品标题',
    description     TEXT                                    COMMENT '详细描述',
    price           DECIMAL(10, 2)  NOT NULL                COMMENT '售价',
    original_price  DECIMAL(10, 2)           DEFAULT NULL   COMMENT '原价, 用于展示折扣',
    cover           VARCHAR(255)             DEFAULT NULL   COMMENT '封面图地址',
    category_id     BIGINT UNSIGNED NOT NULL                COMMENT '分类 ID',
    seller_id       BIGINT UNSIGNED NOT NULL                COMMENT '卖家 ID',
    condition_level VARCHAR(16)     NOT NULL DEFAULT '九成新' COMMENT '成色',
    status          VARCHAR(16)     NOT NULL DEFAULT 'ON_SALE' COMMENT 'ON_SALE 在售 / LOCKED 已锁定 / SOLD 已售出 / OFF_SHELF 已下架',
    view_count      INT             NOT NULL DEFAULT 0      COMMENT '浏览量',
    favorite_count  INT             NOT NULL DEFAULT 0      COMMENT '收藏数',
    version         INT             NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    deleted         TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0 未删, 1 已删',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_category (category_id),
    KEY idx_seller (seller_id, create_time),
    KEY idx_status_create (status, create_time),
    KEY idx_title (title)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品表';

-- -----------------------------------------------------------------------------
-- 4. 商品图片表 (一个商品多张图, 一对多)
-- -----------------------------------------------------------------------------
CREATE TABLE product_image (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    product_id  BIGINT UNSIGNED NOT NULL                COMMENT '商品 ID',
    url         VARCHAR(255)    NOT NULL                COMMENT '图片地址',
    sort_no     INT             NOT NULL DEFAULT 0      COMMENT '排序值',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_product (product_id, sort_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品图片表';

-- -----------------------------------------------------------------------------
-- 5. 订单表
--    status: CREATED 待卖家确认 -> CONFIRMED 卖家已确认 -> FINISHED 已完成
--            CREATED / CONFIRMED 均可 -> CANCELLED 已取消
--    冗余 product_title / product_cover 作为下单时的快照, 商品被改价也不影响历史订单
-- -----------------------------------------------------------------------------
CREATE TABLE product_order (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no      VARCHAR(32)     NOT NULL                COMMENT '业务订单号',
    product_id    BIGINT UNSIGNED NOT NULL                COMMENT '商品 ID',
    product_title VARCHAR(100)    NOT NULL                COMMENT '商品标题快照',
    product_cover VARCHAR(255)             DEFAULT NULL   COMMENT '商品封面快照',
    buyer_id      BIGINT UNSIGNED NOT NULL                COMMENT '买家 ID',
    seller_id     BIGINT UNSIGNED NOT NULL                COMMENT '卖家 ID',
    amount        DECIMAL(10, 2)  NOT NULL                COMMENT '成交金额',
    status        VARCHAR(16)     NOT NULL DEFAULT 'CREATED' COMMENT '订单状态',
    remark        VARCHAR(255)             DEFAULT NULL   COMMENT '买家留言',
    contact       VARCHAR(64)              DEFAULT NULL   COMMENT '联系方式',
    cancel_reason VARCHAR(255)             DEFAULT NULL   COMMENT '取消原因',
    create_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirm_time  DATETIME                 DEFAULT NULL   COMMENT '卖家确认时间',
    finish_time   DATETIME                 DEFAULT NULL   COMMENT '交易完成时间',
    cancel_time   DATETIME                 DEFAULT NULL   COMMENT '取消时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_buyer (buyer_id, create_time),
    KEY idx_seller (seller_id, create_time),
    KEY idx_product (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '订单表';

-- -----------------------------------------------------------------------------
-- 6. 商品留言表
-- -----------------------------------------------------------------------------
CREATE TABLE comment (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    product_id  BIGINT UNSIGNED NOT NULL                COMMENT '商品 ID',
    user_id     BIGINT UNSIGNED NOT NULL                COMMENT '留言用户 ID',
    content     VARCHAR(500)    NOT NULL                COMMENT '留言内容',
    parent_id   BIGINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '父留言 ID, 0 表示一级留言',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_product (product_id, create_time),
    KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品留言表';

-- -----------------------------------------------------------------------------
-- 7. 收藏表 (user_id + product_id 唯一, 保证不会重复收藏)
-- -----------------------------------------------------------------------------
CREATE TABLE favorite (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT UNSIGNED NOT NULL                COMMENT '用户 ID',
    product_id  BIGINT UNSIGNED NOT NULL                COMMENT '商品 ID',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_product (user_id, product_id),
    KEY idx_user (user_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '收藏表';

-- -----------------------------------------------------------------------------
-- 8. 信用分变动流水表
-- -----------------------------------------------------------------------------
CREATE TABLE credit_log (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id      BIGINT UNSIGNED NOT NULL                COMMENT '用户 ID',
    change_score INT             NOT NULL                COMMENT '变动值, 可正可负',
    reason       VARCHAR(100)    NOT NULL                COMMENT '变动原因',
    order_id     BIGINT UNSIGNED          DEFAULT NULL   COMMENT '关联订单 ID',
    create_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user (user_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '信用分流水表';

-- -----------------------------------------------------------------------------
-- 初始化分类数据
-- -----------------------------------------------------------------------------
INSERT INTO category (name, icon, sort_no) VALUES
    ('教材书籍', '书', 1),
    ('数码电子', '机', 2),
    ('生活用品', '物', 3),
    ('运动器材', '动', 4),
    ('服饰鞋包', '衣', 5),
    ('乐器文娱', '乐', 6),
    ('代步工具', '行', 7),
    ('其他闲置', '他', 8);

SET FOREIGN_KEY_CHECKS = 1;
