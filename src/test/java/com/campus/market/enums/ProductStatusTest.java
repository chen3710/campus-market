package com.campus.market.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class ProductStatusTest {

    @Test
    @DisplayName("只有在售状态可以交易")
    void onlyOnSaleIsTradable() {
        assertThat(ProductStatus.ON_SALE.isTradable()).isTrue();
        assertThat(ProductStatus.LOCKED.isTradable()).isFalse();
        assertThat(ProductStatus.SOLD.isTradable()).isFalse();
        assertThat(ProductStatus.OFF_SHELF.isTradable()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(ProductStatus.class)
    @DisplayName("每个状态都有中文描述")
    void everyStatusHasDescription(ProductStatus status) {
        assertThat(status.getDesc()).isNotBlank();
    }
}
