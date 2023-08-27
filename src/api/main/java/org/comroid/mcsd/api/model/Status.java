package org.comroid.mcsd.api.model;

import lombok.Getter;
import lombok.Value;
import org.comroid.api.IntegerAttribute;
import org.comroid.mcsd.util.McFormatCode;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

@Getter
public enum Status implements IntegerAttribute, IStatusMessage {
    unknown_status  ("❔", McFormatCode.Dark_Gray),
    offline         ("❌", McFormatCode.Dark_Red),
    starting        ("⏯️", McFormatCode.Aqua),
    maintenance     ("\uD83D\uDD27", McFormatCode.Yellow),
    running_backup  ("\uD83D\uDCBE", McFormatCode.Green),
    updating        ("\uD83D\uDD04️", McFormatCode.Light_Purple),
    in_Trouble      ("⚠️", McFormatCode.Gold),
    online          ("✅", McFormatCode.Dark_Green),
    shutting_down   ("\uD83D\uDED1", McFormatCode.Red);

    private final String emoji;
    private final Color color;

    Status(String emoji, McFormatCode color) {
        if (!color.isColor())
            throw new IllegalArgumentException("Invalid format code; must be color: ");
        this.emoji = emoji;
        this.color = color.getColor();
    }

    @Override
    public String getName() {
        return IntegerAttribute.super.getName().replace('_',' ');
    }

    @Override
    public String getAlternateName() {
        return getEmoji();
    }

    @Override
    public Status getStatus() {
        return this;
    }

    @Value
    @SuppressWarnings("InnerClassMayBeStatic")
    public class Message implements IStatusMessage {
        @Nullable String message;

        public Status getStatus() {
            return Status.this;
        }
    }
}
