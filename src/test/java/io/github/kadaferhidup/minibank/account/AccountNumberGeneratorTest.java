package io.github.kadaferhidup.minibank.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AccountNumberGeneratorTest {

    private final AccountNumberGenerator generator = new AccountNumberGenerator();

    @Test
    void generatedNumbersAreTenDigitsWithValidCheckDigit() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String n = generator.next();
            assertThat(n).hasSize(10).startsWith("10").containsOnlyDigits();
            assertThat(AccountNumberGenerator.isValid(n)).isTrue();
            seen.add(n);
        }
        assertThat(seen).hasSizeGreaterThan(990);
    }

    @Test
    void singleDigitTypoIsDetected() {
        String n = generator.next();
        for (int pos = 0; pos < 10; pos++) {
            int d = n.charAt(pos) - '0';
            String typo = n.substring(0, pos) + ((d + 1) % 10) + n.substring(pos + 1);
            assertThat(AccountNumberGenerator.isValid(typo)).as("typo di posisi %d", pos).isFalse();
        }
    }

    @Test
    void knownLuhnExample() {
        // 7992739871 adalah contoh Luhn klasik dengan digit pemeriksa 3
        assertThat(AccountNumberGenerator.checkDigit("7992739871")).isEqualTo(3);
        assertThat(AccountNumberGenerator.isValid("abc")).isFalse();
        assertThat(AccountNumberGenerator.isValid(null)).isFalse();
    }
}
