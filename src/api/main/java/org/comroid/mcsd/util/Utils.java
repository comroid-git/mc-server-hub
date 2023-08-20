package org.comroid.mcsd.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

public class Utils {
    @Contract("null -> null; _ -> _")
    public static String removeAnsiEscapeSequences(@Nullable String input) {
        return input == null ? null : input.replaceAll("\u001B[\\[(][?;\\d]*[=a-zA-Z]", "");
    }
}
