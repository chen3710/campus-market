package com.campus.market.web;

import com.campus.market.entity.ProductOrder;
import com.campus.market.enums.OrderStatus;
import com.campus.market.mapper.ProductOrderMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 超时订单查询的 SQL 行为测试。
 * <p>
 * 为什么单靠单元测试不够：{@code selectTimeoutOrders} 的核心逻辑是
 * 「CREATED 看 create_time、CONFIRMED 看 confirm_time」这条 SQL 条件，
 * Mockito 把它整个 mock 掉了，等于什么都没验证。只有真的建表、真的插数据、
 * 真的跑一遍 SQL，才能确认这条条件写对了。
 * <p>
 * 跑在 h2 上，用 JdbcTemplate 把时间戳改到过去来模拟「已经挂了很久」的订单 ——
 * 总比在测试里 sleep 半小时强。
 * <p>
 * 注：刻意与 {@link ApiIntegrationTest} 用同一组注解，让 Spring 复用同一个上下文，
 * 省掉一次容器启动。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@TestPropertySource(properties = "app.order.enabled=false")
@DisplayName("超时订单查询：时间戳判定逻辑")
class OrderTimeoutQueryIntegrationTest {

    /** 判定线：早于「当前时间 - 30 分钟」仍未推进的订单算超时 */
    private static final LocalDateTime DEADLINE = LocalDateTime.now().minusMinutes(30);
    private static final LocalDateTime LONG_AGO = LocalDateTime.now().minusHours(2);
    private static final LocalDateTime JUST_NOW = LocalDateTime.now().minusSeconds(5);

    @Autowired
    private ProductOrderMapper orderMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 直接建一笔订单，状态由参数指定 */
    private Long insertOrder(OrderStatus status) {
        ProductOrder order = new ProductOrder();
        order.setOrderNo("IT" + System.nanoTime());
        order.setProductId(1L);
        order.setProductTitle("超时查询集成测试");
        order.setBuyerId(2L);
        order.setSellerId(3L);
        order.setAmount(new BigDecimal("9.90"));
        order.setStatus(status.name());
        orderMapper.insert(order);
        return order.getId();
    }

    /**
     * 把订单的时间戳改到过去。
     * 用 SQL 直接改，因为 create_time 是 MetaObjectHandler 在插入时自动填的，
     * 走实体更新反而更绕。
     */
    private void backdate(Long orderId, String column, LocalDateTime value) {
        jdbcTemplate.update("UPDATE product_order SET " + column + " = ? WHERE id = ?",
                value, orderId);
    }

    private Set<Long> timeoutIds(int limit) {
        List<ProductOrder> orders = orderMapper.selectTimeoutOrders(DEADLINE, limit);
        return orders.stream().map(ProductOrder::getId).collect(Collectors.toSet());
    }

    @Test
    @DisplayName("刚创建的订单不算超时")
    void freshOrderIsNotTimeout() {
        Long id = insertOrder(OrderStatus.CREATED);
        assertThat(timeoutIds(500)).doesNotContain(id);
    }

    @Test
    @DisplayName("CREATED 订单：create_time 超过阈值后被捞出")
    void staleCreatedOrderIsFound() {
        Long id = insertOrder(OrderStatus.CREATED);
        backdate(id, "create_time", LONG_AGO);
        assertThat(timeoutIds(500)).contains(id);
    }

    @Test
    @DisplayName("CONFIRMED 订单：按 confirm_time 判定，而不是 create_time")
    void confirmedOrderJudgedByConfirmTime() {
        // 场景：很早下单，但卖家刚刚才确认。此时不该被当成超时 ——
        // 用 create_time 判定的实现会在这里出错，这条断言就是专门守住它的
        Long id = insertOrder(OrderStatus.CONFIRMED);
        backdate(id, "create_time", LONG_AGO);
        backdate(id, "confirm_time", JUST_NOW);
        assertThat(timeoutIds(500))
                .as("刚确认的订单不应被判定为超时")
                .doesNotContain(id);

        // 卖家确认很久了却一直没完成交易，这才算超时
        backdate(id, "confirm_time", LONG_AGO);
        assertThat(timeoutIds(500))
                .as("确认后长时间未完成的订单应被捞出")
                .contains(id);
    }

    @Test
    @DisplayName("终态订单即使时间很老也不会被捞出")
    void finalStatesAreNeverPicked() {
        Long finished = insertOrder(OrderStatus.FINISHED);
        Long cancelled = insertOrder(OrderStatus.CANCELLED);
        backdate(finished, "create_time", LONG_AGO);
        backdate(cancelled, "create_time", LONG_AGO);

        Set<Long> ids = timeoutIds(500);
        assertThat(ids).doesNotContain(finished, cancelled);
    }

    @Test
    @DisplayName("limit 生效，不会一次把全部超时订单捞出来")
    void limitIsRespected() {
        for (int i = 0; i < 3; i++) {
            Long id = insertOrder(OrderStatus.CREATED);
            backdate(id, "create_time", LONG_AGO);
        }
        assertThat(orderMapper.selectTimeoutOrders(DEADLINE, 2)).hasSize(2);
        assertThat(orderMapper.selectTimeoutOrders(DEADLINE, 500).size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("超时取消后订单离开候选集（幂等，不会被反复处理）")
    void cancelledOrderLeavesCandidateSet() {
        Long id = insertOrder(OrderStatus.CREATED);
        backdate(id, "create_time", LONG_AGO);
        assertThat(timeoutIds(500)).contains(id);

        // 模拟被取消
        orderMapper.casCancel(id, OrderStatus.CREATED.name(), LocalDateTime.now(), "超时未处理，系统自动取消");
        assertThat(timeoutIds(500))
                .as("已取消的订单不应再次进入候选集")
                .doesNotContain(id);
    }

    @Test
    @DisplayName("casCancel 的条件不匹配时不产生任何修改")
    void casCancelDoesNothingWhenStatusMismatch() {
        Long id = insertOrder(OrderStatus.CREATED);

        // 用错误的状态去取消（订单实际是 CREATED，这里传 CONFIRMED）
        int affected = orderMapper.casCancel(id, OrderStatus.CONFIRMED.name(),
                LocalDateTime.now(), "不该生效");

        assertThat(affected).as("状态不匹配时影响行数应为 0").isZero();
        // 确认订单状态没被改动
        assertThat(orderMapper.selectById(id).getStatus()).isEqualTo(OrderStatus.CREATED.name());
    }
}
