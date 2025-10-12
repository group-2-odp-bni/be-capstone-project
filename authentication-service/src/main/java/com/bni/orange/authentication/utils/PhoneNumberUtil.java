package com.bni.orange.authentication.utils;


public final class PhoneNumberUtil {

    private PhoneNumberUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("Phone number cannot be null or blank");
        }

        if (phoneNumber.startsWith("08")) {
            return "+628" + phoneNumber.substring(2);
        }

        return phoneNumber;
    }
}
