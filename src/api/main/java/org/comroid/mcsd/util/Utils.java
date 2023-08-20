package org.comroid.mcsd.util;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class Utils {
    public static final UUID[] SuperAdmins = new UUID[]{UUID.fromString("48a84b30-df71-4cff-91bd-e82fd8e58c35")};

    @Contract("null -> null; _ -> _")
    public static String removeAnsiEscapeSequences(@Nullable String input) {
        return input == null ? null : input.replaceAll("\u001B[\\[(][?;\\d]*[=a-zA-Z]", "");
    }
}
