-- =============================================================================
-- H2 兼容模式建表脚本（仅供 h2 profile 使用，用于在没有 MySQL 的环境下快速体验）
--
-- 与 sql/init.sql 的差异：
--   1. 去掉 ENGINE / CHARSET / COMMENT 等 MySQL 专有子句
--   2. BIGINT UNSIGNED -> BIGINT（H2 不支持 UNSIGNED）
--   3. 索引改为独立的 CREATE INDEX 语句（H2 不支持建表内联 KEY）
--   4. 去掉 ON UPDATE CURRENT_TIMESTAMP（H2 不支持），
--      update_time 由 MyBatis-Plus 的 MetaObjectHandler 负责填充
-- =============================================================================

DROP TABLE IF EXISTS credit_log;
DROP TABLE IF EXISTS favorite;
DROP TABLE IF EXISTS comment;
DROP TABLE IF EXISTS product_order;
DROP TABLE IF EXISTS product_image;
DROP TABLE IF EXISTS product;
DROP TABLE IF EXISTS category;
DROP TABLE IF EXISTS `user`;

CREATE TABLE `user` (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    username     VARCHAR(32)  NOT NULL,
    password     VARCHAR(100) NOT NULL,
    nickname     VARCHAR(32)  NOT NULL,
    avatar       VARCHAR(255) DEFAULT NULL,
    phone        VARCHAR(20)  DEFAULT NULL,
    campus       VARCHAR(64)  DEFAULT NULL,
    credit_score INT          NOT NULL DEFAULT 100,
    role         VARCHAR(16)  NOT NULL DEFAULT 'USER',
    status       TINYINT      NOT NULL DEFAULT 1,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_user_username ON `user` (username);
CREATE INDEX idx_user_status ON `user` (status);

CREATE TABLE category (
    id      BIGINT      NOT NULL AUTO_INCREMENT,
    name    VARCHAR(32) NOT NULL,
    icon    VARCHAR(16) DEFAULT NULL,
    sort_no INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_category_name ON category (name);

CREATE TABLE product (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    title           VARCHAR(100)   NOT NULL,
    description     VARCHAR(4000)  DEFAULT NULL,
    price           DECIMAL(10, 2) NOT NULL,
    original_price  DECIMAL(10, 2) DEFAULT NULL,
    cover           VARCHAR(255)   DEFAULT NULL,
    category_id     BIGINT         NOT NULL,
    seller_id       BIGINT         NOT NULL,
    condition_level VARCHAR(16)    NOT NULL DEFAULT '九成新',
    status          VARCHAR(16)    NOT NULL DEFAULT 'ON_SALE',
    view_count      INT            NOT NULL DEFAULT 0,
    favorite_count  INT            NOT NULL DEFAULT 0,
    version         INT            NOT NULL DEFAULT 0,
    deleted         TINYINT        NOT NULL DEFAULT 0,
    create_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_product_category ON product (category_id);
CREATE INDEX idx_product_seller ON product (seller_id, create_time);
CREATE INDEX idx_product_status_create ON product (status, create_time);

CREATE TABLE product_image (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    product_id  BIGINT       NOT NULL,
    url         VARCHAR(255) NOT NULL,
    sort_no     INT          NOT NULL DEFAULT 0,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_image_product ON product_image (product_id, sort_no);

CREATE TABLE product_order (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    order_no      VARCHAR(32)    NOT NULL,
    product_id    BIGINT         NOT NULL,
    product_title VARCHAR(100)   NOT NULL,
    product_cover VARCHAR(255)   DEFAULT NULL,
    buyer_id      BIGINT         NOT NULL,
    seller_id     BIGINT         NOT NULL,
    amount        DECIMAL(10, 2) NOT NULL,
    status        VARCHAR(16)    NOT NULL DEFAULT 'CREATED',
    remark        VARCHAR(255)   DEFAULT NULL,
    contact       VARCHAR(64)    DEFAULT NULL,
    cancel_reason VARCHAR(255)   DEFAULT NULL,
    create_time   DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirm_time  DATETIME       DEFAULT NULL,
    finish_time   DATETIME       DEFAULT NULL,
    cancel_time   DATETIME       DEFAULT NULL,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_order_no ON product_order (order_no);
CREATE INDEX idx_order_buyer ON product_order (buyer_id, create_time);
CREATE INDEX idx_order_seller ON product_order (seller_id, create_time);

CREATE TABLE comment (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    product_id  BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    content     VARCHAR(500) NOT NULL,
    parent_id   BIGINT       NOT NULL DEFAULT 0,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_comment_product ON comment (product_id, create_time);

CREATE TABLE favorite (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    user_id     BIGINT   NOT NULL,
    product_id  BIGINT   NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_favorite_user_product ON favorite (user_id, product_id);

CREATE TABLE credit_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    change_score INT          NOT NULL,
    reason       VARCHAR(100) NOT NULL,
    order_id     BIGINT       DEFAULT NULL,
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX idx_credit_user ON credit_log (user_id, create_time);

INSERT INTO category (name, icon, sort_no) VALUES
    ('教材书籍', '书', 1),
    ('数码电子', '机', 2),
    ('生活用品', '物', 3),
    ('运动器材', '动', 4),
    ('服饰鞋包', '衣', 5),
    ('乐器文娱', '乐', 6),
    ('代步工具', '行', 7),
    ('其他闲置', '他', 8);
