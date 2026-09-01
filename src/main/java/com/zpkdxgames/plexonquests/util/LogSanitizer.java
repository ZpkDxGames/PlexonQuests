package com.zpkdxgames.plexonquests.util;

public final class LogSanitizer {
    private LogSanitizer() {}

    public static String clean(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder output = new StringBuilder(Math.min(input.length(), 512));
        for (int index = 0; index < input.length() && output.length() < 512; index++) {
            char character = input.charAt(index);
            if (character >= 0x20 && character != 0x7f) {
                output.append(character);
            } else {
                output.append(' ');
            }
        }
        return output.toString();
    }
}

