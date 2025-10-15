package com.bni.orange.users.util;

import java.security.SecureRandom;

public final class OtpGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int OTP_LENGTH = 6;
    private static final int OTP_MIN = 100000;
    private static final int OTP_MAX = 999999;

    private OtpGenerator() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static String generate() {
        int otp = RANDOM.nextInt(OTP_MAX - OTP_MIN + 1) + OTP_MIN;
        return String.valueOf(otp);
    }

    public static String generate(int length) {
        if (length < 4 || length > 10) {
            throw new IllegalArgumentException("OTP length must be between 4 and 10");
        }

        StringBuilder otp = new StringBuilder();
        for (int i = 0; i < length; i++) {
            otp.append(RANDOM.nextInt(10));
        }

        if (otp.charAt(0) == '0') {
            otp.setCharAt(0, (char) ('1' + RANDOM.nextInt(9)));
        }

        return otp.toString();
    }

    public static boolean isValid(String otp) {
        if (otp == null || otp.length() != OTP_LENGTH) {
            return false;
        }
        return otp.matches("\\d{6}");
    }
}
