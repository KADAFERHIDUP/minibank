package io.github.kadaferhidup.minibank.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

public final class Money {

    private Money() {
    }

    /** Nominal wajib positif dan paling banyak 2 angka di belakang koma. */
    public static BigDecimal normalize(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new ApiException(ErrorCode.INVALID_AMOUNT, "Nominal harus lebih dari 0");
        }
        BigDecimal stripped = amount.stripTrailingZeros();
        if (stripped.scale() > 2) {
            throw new ApiException(ErrorCode.INVALID_AMOUNT, "Nominal maksimal 2 angka di belakang koma");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    public static String rupiah(BigDecimal amount) {
        NumberFormat f = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID"));
        f.setMaximumFractionDigits(0);
        return f.format(amount);
    }
}
