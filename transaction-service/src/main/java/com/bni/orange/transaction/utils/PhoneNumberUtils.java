package com.bni.orange.transaction.utils;

import lombok.experimental.UtilityClass;

import java.util.Optional;

@UtilityClass
public class PhoneNumberUtils {

    public static String normalize(String phoneNumber) {
        return Optional.ofNullable(phoneNumber)
            .filter(s -> !s.isBlank())
            .map(s -> {
                var cleaned = s.replaceAll("[^+\\d]", "");
                if (cleaned.startsWith("+")) {
                    return cleaned;
                }
                if (cleaned.startsWith("0")) {
                    return "+62" + cleaned.substring(1);
                }
                if (cleaned.startsWith("62")) {
                    return "+" + cleaned;
                }
                return "+62" + cleaned;
            })
            .orElse(phoneNumber);
    }

    public static String format(String phoneNumber) {
        return Optional.ofNullable(phoneNumber)
            .filter(s -> !s.isBlank())
            .map(PhoneNumberUtils::normalize)
            .map(normalized -> {
                if (normalized.startsWith("+62")) {
                    return formatIndonesianNumber(normalized);
                }
                return normalized;
            })
            .orElse(phoneNumber);
    }

    public static boolean isValid(String phoneNumber) {
        return Optional.ofNullable(phoneNumber)
            .map(PhoneNumberUtils::normalize)
            .map(normalized -> normalized.matches("^\\+[1-9]\\d{1,14}$"))
            .orElse(false);
    }

    private static String formatIndonesianNumber(String normalizedNumber) {
        var withoutCountry = "0" + normalizedNumber.substring(3);
        if (withoutCountry.length() >= 11) {
            return String.join(" ",
                withoutCountry.substring(0, 4),
                withoutCountry.substring(4, 8),
                withoutCountry.substring(8)
            );
        }
        return withoutCountry;
    }
}
