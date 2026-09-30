package com.campus.market.enums;

import com.campus.market.common.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 订单状态机单元测试。
 * <p>
 * 状态机是这个项目里最值得单测的部分：它是纯函数、没有外部依赖、跑得飞快，
 * 而且一旦流转规则被改错，会造成线上数据状态错乱 —— 正是单测该守住的边界。
 */
class OrderStatusTest {

    @Test
    @DisplayName("CREATED 只能流转到 CONFIRMED 或 CANCELLED")
    void createdCanOnlyGoToConfirmedOrCancelled() {
        assertThat(OrderStatus.CREATED.canTransferTo(OrderStatus.CONFIRMED)).isTrue();
        assertThat(OrderStatus.CREATED.canTransferTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.CREATED.canTransferTo(OrderStatus.FINISHED)).isFalse();
        assertThat(OrderStatus.CREATED.canTransferTo(OrderStatus.CREATED)).isFalse();
    }

    @Test
    @DisplayName("CONFIRMED 只能流转到 FINISHED 或 CANCELLED")
    void confirmedCanOnlyGoToFinishedOrCancelled() {
        assertThat(OrderStatus.CONFIRMED.canTransferTo(OrderStatus.FINISHED)).isTrue();
        assertThat(OrderStatus.CONFIRMED.canTransferTo(OrderStatus.CANCELLED)).isTrue();
        assertThat(OrderStatus.CONFIRMED.canTransferTo(OrderStatus.CREATED)).isFalse();
        assertThat(OrderStatus.CONFIRMED.canTransferTo(OrderStatus.CONFIRMED)).isFalse();
    }

    @Test
    @DisplayName("FINISHED 和 CANCELLED 是终态，不能再流转到任何状态")
    void finalStatesAreTerminal() {
        for (OrderStatus target : OrderStatus.values()) {
            assertThat(OrderStatus.FINISHED.canTransferTo(target))
                    .as("FINISHED 不应能流转到 %s", target)
                    .isFalse();
            assertThat(OrderStatus.CANCELLED.canTransferTo(target))
                    .as("CANCELLED 不应能流转到 %s", target)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("只有终态会被 isFinal 判定为 true")
    void isFinalOnlyForTerminalStates() {
        assertThat(OrderStatus.CREATED.isFinal()).isFalse();
        assertThat(OrderStatus.CONFIRMED.isFinal()).isFalse();
        assertThat(OrderStatus.FINISHED.isFinal()).isTrue();
        assertThat(OrderStatus.CANCELLED.isFinal()).isTrue();
    }

    @Test
    @DisplayName("只有 CREATED / CONFIRMED 会占用商品")
    void onlyActiveStatesOccupyProduct() {
        assertThat(OrderStatus.CREATED.isActive()).isTrue();
        assertThat(OrderStatus.CONFIRMED.isActive()).isTrue();
        assertThat(OrderStatus.FINISHED.isActive()).isFalse();
        assertThat(OrderStatus.CANCELLED.isActive()).isFalse();
    }

    @Test
    @DisplayName("assertTransferTo 对合法流转不抛异常")
    void assertTransferToPassesForLegalTransition() {
        assertThatCode(() -> OrderStatus.CREATED.assertTransferTo(OrderStatus.CONFIRMED))
                .doesNotThrowAnyException();
        assertThatCode(() -> OrderStatus.CONFIRMED.assertTransferTo(OrderStatus.FINISHED))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("assertTransferTo 对非法流转抛出带状态描述的业务异常")
    void assertTransferToThrowsForIllegalTransition() {
        // 刻意不硬编码「已取消」「待面交」这类字面量：文案随时可能调整，
        // 断言里引用枚举自身的 desc，才能既验证「消息同时包含源状态与目标状态」
        // 这一语义，又不会因为改文案而假失败。
        assertThatThrownBy(() -> OrderStatus.CANCELLED.assertTransferTo(OrderStatus.CONFIRMED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(OrderStatus.CANCELLED.getDesc())
                .hasMessageContaining(OrderStatus.CONFIRMED.getDesc());
    }

    /**
     * 把完整的流转矩阵写死成一张表来断言。
     * 这样将来有人改动 TRANSITIONS 里的规则时，测试会立刻失败并明确指出是哪一条被改了。
     */
    @Test
    @DisplayName("完整流转矩阵：只有下表中标 Y 的组合才允许")
    void fullTransitionMatrix() {
        String[][] matrix = {
                //              CONFIRMED  FINISHED  CANCELLED  CREATED
                /* CREATED   */ {"Y", "N", "Y", "N"},
                /* CONFIRMED */ {"N", "Y", "Y", "N"},
                /* FINISHED  */ {"N", "N", "N", "N"},
                /* CANCELLED */ {"N", "N", "N", "N"}
        };
        OrderStatus[] rows = {OrderStatus.CREATED, OrderStatus.CONFIRMED,
                OrderStatus.FINISHED, OrderStatus.CANCELLED};
        OrderStatus[] cols = {OrderStatus.CONFIRMED, OrderStatus.FINISHED,
                OrderStatus.CANCELLED, OrderStatus.CREATED};

        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < cols.length; c++) {
                boolean expected = "Y".equals(matrix[r][c]);
                assertThat(rows[r].canTransferTo(cols[c]))
                        .as("%s -> %s 应为 %s", rows[r], cols[c], expected ? "允许" : "禁止")
                        .isEqualTo(expected);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    @DisplayName("每个状态都有中文描述，供前端直接展示")
    void everyStatusHasDescription(OrderStatus status) {
        assertThat(status.getDesc()).isNotBlank();
    }
}
