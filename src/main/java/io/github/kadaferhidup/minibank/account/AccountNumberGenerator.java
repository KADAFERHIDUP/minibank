package io.github.kadaferhidup.minibank.account;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Nomor rekening 10 digit: awalan "10", 7 digit acak, lalu 1 digit pemeriksa Luhn.
 * Digit pemeriksa membuat salah ketik satu angka langsung ketahuan sebelum transfer.
 */
@Component
public class AccountNumberGenerator {

    private static final String PREFIX = "10";
    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder body = new StringBuilder(PREFIX);
        for (int i = 0; i < 7; i++) {
            body.append(random.nextInt(10));
        }
        return body.append(checkDigit(body.toString())).toString();
    }

    public static boolean isValid(String number) {
        if (number == null || !number.matches("\\d{10}")) {
            return false;
        }
        return checkDigit(number.substring(0, 9)) == number.charAt(9) - '0';
    }

    static int checkDigit(String digits) {
        int sum = 0;
        boolean dbl = true; // digit paling kanan dari payload digandakan
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (dbl) {
                d *= 2;
                if (d > 9) {
                    d -= 9;
                }
            }
            sum += d;
            dbl = !dbl;
        }
        return (10 - sum % 10) % 10;
    }
}
