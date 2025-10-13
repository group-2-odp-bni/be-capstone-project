package com.bni.orange.authentication.validator;

import com.bni.orange.authentication.error.BusinessException;
import com.bni.orange.authentication.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class PinValidator {

    private static final int MAX_CONSECUTIVE_SAME_DIGITS = 4;

    private static final Set<String> BLACKLISTED_PINS = Set.of(
        "000000", "111111", "222222", "333333", "444444", "555555", "666666", "777777", "888888", "999999",
        "123456", "654321", "123123", "234567", "345678", "456789", "567890",
        "121212", "131313", "141414", "151515", "161616", "171717", "181818", "191919",
        "101010", "202020", "303030", "404040", "505050", "606060", "707070", "808080", "909090",
        "112233", "223344", "334455", "445566", "556677", "667788", "778899",
        "102030", "010203"
    );

    public void validate(String pin) {
        if (pin == null || pin.length() != 6) {
            throw new BusinessException(ErrorCode.INVALID_PIN);
        }

        if (!pin.matches("\\d{6}")) {
            throw new BusinessException(ErrorCode.INVALID_PIN);
        }

        if (BLACKLISTED_PINS.contains(pin)) {
            throw new BusinessException(ErrorCode.INVALID_PIN, "PIN is too weak. Please choose a different PIN.");
        }

        if (isSequentialAscending(pin)) {
            throw new BusinessException(ErrorCode.INVALID_PIN, "PIN contains sequential numbers. Please choose a different PIN.");
        }

        if (isSequentialDescending(pin)) {
            throw new BusinessException(ErrorCode.INVALID_PIN, "PIN contains sequential numbers. Please choose a different PIN.");
        }

        if (hasRepeatingPattern(pin)) {
            throw new BusinessException(ErrorCode.INVALID_PIN, "PIN has too many repeating digits. Please choose a different PIN.");
        }
    }

    private boolean isSequentialAscending(String pin) {
        for (int i = 0; i < pin.length() - 1; i++) {
            int current = Character.getNumericValue(pin.charAt(i));
            int next = Character.getNumericValue(pin.charAt(i + 1));

            if (next != (current + 1) % 10) {
                return false;
            }
        }
        return true;
    }

    private boolean isSequentialDescending(String pin) {
        for (int i = 0; i < pin.length() - 1; i++) {
            int current = Character.getNumericValue(pin.charAt(i));
            int next = Character.getNumericValue(pin.charAt(i + 1));

            if (next != (current - 1 + 10) % 10) {
                return false;
            }
        }
        return true;
    }

    private boolean hasRepeatingPattern(String pin) {
        int maxConsecutive = 1;
        int currentConsecutive = 1;

        for (int i = 1; i < pin.length(); i++) {
            if (pin.charAt(i) == pin.charAt(i - 1)) {
                currentConsecutive++;
                maxConsecutive = Math.max(maxConsecutive, currentConsecutive);
            } else {
                currentConsecutive = 1;
            }
        }

        return maxConsecutive > MAX_CONSECUTIVE_SAME_DIGITS;
    }
}
