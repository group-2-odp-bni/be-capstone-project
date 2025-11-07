package com.bni.orange.authentication.util;


public final class PhoneNumberUtil {

    private PhoneNumberUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("Phone number cannot be null or blank");
        }

        // Remove all non-digit characters except leading +
        String cleaned = phoneNumber.replaceAll("[^0-9+]", "");

        // Already in international format +628...
        if (cleaned.startsWith("+628")) {
            return cleaned;
        }

        // Format: 08xxxxxxxxx -> +628xxxxxxxxx
        if (cleaned.startsWith("08")) {
            return "+628" + cleaned.substring(2);
        }

        // Format: 8xxxxxxxxx -> +628xxxxxxxxx
        if (cleaned.startsWith("8")) {
            return "+628" + cleaned.substring(1);
        }

        // Fallback: if starts with +62 but not +628, keep as is
        if (cleaned.startsWith("+62")) {
            return cleaned;
        }

        throw new IllegalArgumentException("Invalid phone number format: " + phoneNumber);
    }
}
