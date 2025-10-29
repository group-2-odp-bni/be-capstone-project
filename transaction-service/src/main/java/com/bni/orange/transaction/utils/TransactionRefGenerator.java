package com.bni.orange.transaction.utils;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Component
public class TransactionRefGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String NUMERIC = "0123456789";
    private static final int RANDOM_LENGTH = 12;

    public String generate() {
        var datePart = LocalDateTime.now().format(DATE_FORMATTER);
        var randomPart = generateRandomString();
        return datePart + randomPart;
    }

    private String generateRandomString() {
        return ThreadLocalRandom.current()
            .ints(TransactionRefGenerator.RANDOM_LENGTH, 0, NUMERIC.length())
            .mapToObj(NUMERIC::charAt)
            .map(Object::toString)
            .collect(Collectors.joining());
    }
}
