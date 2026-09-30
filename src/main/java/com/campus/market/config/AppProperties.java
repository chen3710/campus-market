package com.campus.market.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用自定义配置，对应 application.yml 中的 app.* 节点。
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Upload upload, DemoData demoData, Order order) {

    public record Jwt(String secret, long expireMinutes, String header, String prefix) {
    }

    public record Upload(String dir, String urlPrefix) {
    }

    public record DemoData(boolean enabled) {
    }

    /**
     * 订单超时自动取消相关配置。
     *
     * @param enabled        是否启用定时扫描
     * @param timeoutMinutes 订单在该状态停留超过多少分钟即视为超时
     * @param scanIntervalMs 扫描间隔（毫秒）
     * @param batchSize      单次最多处理多少笔，避免一次拉太多把内存和锁都占满
     */
    public record Order(boolean enabled, long timeoutMinutes, long scanIntervalMs, int batchSize) {
    }
}
