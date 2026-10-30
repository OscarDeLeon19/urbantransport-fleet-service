package com.urbantransport.fleet.util;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordGeneratorTest {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "#$%&*+-=?@";
    private static final String ALLOWED = UPPER + LOWER + DIGITS + SYMBOLS;

    @RepeatedTest(200)
    void temporary_hasTwelveCharactersWithEveryCategory() {
        String password = PasswordGenerator.temporary();

        assertEquals(12, password.length());
        assertTrue(containsAny(password, UPPER), () -> "sin mayúscula: " + password);
        assertTrue(containsAny(password, LOWER), () -> "sin minúscula: " + password);
        assertTrue(containsAny(password, DIGITS), () -> "sin dígito: " + password);
        assertTrue(containsAny(password, SYMBOLS), () -> "sin símbolo: " + password);
    }

    @RepeatedTest(200)
    void temporary_usesOnlyUnambiguousCharacters() {
        String password = PasswordGenerator.temporary();

        password.chars().forEach(character -> assertTrue(ALLOWED.indexOf(character) >= 0,
                () -> "carácter no permitido '" + (char) character + "' en " + password));
    }

    @Test
    void temporary_doesNotRepeatPasswords() {
        Set<String> passwords = new HashSet<>();
        IntStream.range(0, 1000).forEach(i -> passwords.add(PasswordGenerator.temporary()));

        assertEquals(1000, passwords.size());
    }

    private static boolean containsAny(String password, String characters) {
        return password.chars().anyMatch(character -> characters.indexOf(character) >= 0);
    }
}
