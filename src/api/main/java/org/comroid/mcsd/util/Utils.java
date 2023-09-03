package org.comroid.mcsd.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class Utils {
    public static final UUID[] SuperAdmins = new UUID[]{
            //UUID.fromString(""), // dev
            UUID.fromString("f31ac5bc-c6b5-4a48-a656-4253fd0528f9") // prod
    };

    @Contract("null -> null; _ -> _")
    public static String removeAnsiEscapeSequences(@Nullable String input) {
        return input == null ? null : input.replaceAll("\u001B[\\[(][?;\\d]*[=a-zA-Z]", "");
    }
}
