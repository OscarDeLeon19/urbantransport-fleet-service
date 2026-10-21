package com.urbantransport.fleet.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PasswordGenerator {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "#$%&*+-=?@";
    private static final String ALL = UPPER + LOWER + DIGITS + SYMBOLS;
    private static final int LENGTH = 12;

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String temporary() {
        List<Character> chars = new ArrayList<>(LENGTH);
        chars.add(randomFrom(UPPER));
        chars.add(randomFrom(LOWER));
        chars.add(randomFrom(DIGITS));
        chars.add(randomFrom(SYMBOLS));
        while (chars.size() < LENGTH) {
            chars.add(randomFrom(ALL));
        }
        Collections.shuffle(chars, RANDOM);

        StringBuilder password = new StringBuilder(LENGTH);
        chars.forEach(password::append);
        return password.toString();
    }

    private static char randomFrom(String source) {
        return source.charAt(RANDOM.nextInt(source.length()));
    }
}
