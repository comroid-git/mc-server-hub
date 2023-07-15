package org.comroid.mcsd.api.util;

public class Utils {
    public static String removeAnsiEscapeSequences(String input) {
        return input.replaceAll("\u001B[\\[(][?;\\d]*[=a-zA-Z]", "");
    }
}
