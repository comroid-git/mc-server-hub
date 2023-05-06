package org.comroid.mcsd.web.util;

public class Utils {
    public static String removeAnsiEscapeSequences(String input) {
        return input.replaceAll("\u001B[\\[(][?;\\d]*[=a-zA-Z]", "");
    }
}
