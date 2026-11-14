package io.github.kadaferhidup.minibank.common;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class Masking {

    private Masking() {
    }

    /** "Ahmad Umraithi" menjadi "Ah*** Um******", seperti cek nama penerima di aplikasi bank. */
    public static String name(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "";
        }
        return Arrays.stream(fullName.trim().split("\\s+"))
                .map(w -> w.length() <= 2 ? w : w.substring(0, 2) + "*".repeat(w.length() - 2))
                .collect(Collectors.joining(" "));
    }
}
