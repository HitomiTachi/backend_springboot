package com.example.webdienthoai.service;

import java.text.Normalizer;
import java.util.Locale;

final class ExcelTextUtil {

    private ExcelTextUtil() {
    }

    static String normalizeHeaderKey(String input) {
        if (input == null) {
            return "";
        }
        String n = Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        n = n.replaceAll("[^a-z0-9]+", " ").trim();
        return n.replaceAll("\\s+", " ");
    }

    static String trimToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }
}
