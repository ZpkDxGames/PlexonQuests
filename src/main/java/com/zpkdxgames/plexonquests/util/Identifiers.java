package com.zpkdxgames.plexonquests.util;

import java.util.regex.Pattern;

public final class Identifiers {
    private static final Pattern VALID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");

    private Identifiers() {}

    public static boolean valid(String value) {
        return value != null && VALID.matcher(value).matches();
    }

    public static String require(String value, String label) {
        if (!valid(value)) {
            throw new IllegalArgumentException(label + " must match " + VALID.pattern());
        }
        return value;
    }
}

