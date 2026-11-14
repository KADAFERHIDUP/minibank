package io.github.kadaferhidup.minibank.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyAndMaskingTest {

    @Test
    void normalizeKeepsTwoDecimals() {
        assertThat(Money.normalize(new BigDecimal("10000"))).isEqualByComparingTo("10000.00");
        assertThat(Money.normalize(new BigDecimal("10000.5"))).hasToString("10000.50");
        assertThat(Money.normalize(new BigDecimal("10000.500"))).hasToString("10000.50");
    }

    @Test
    void normalizeRejectsInvalidAmounts() {
        assertThatThrownBy(() -> Money.normalize(BigDecimal.ZERO)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Money.normalize(new BigDecimal("-1"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Money.normalize(new BigDecimal("1.234"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> Money.normalize(null)).isInstanceOf(ApiException.class);
    }

    @Test
    void maskName() {
        assertThat(Masking.name("Ahmad Umraithi")).isEqualTo("Ah*** Um******");
        assertThat(Masking.name("  Al  Bo ")).isEqualTo("Al Bo");
        assertThat(Masking.name(null)).isEmpty();
    }
}
