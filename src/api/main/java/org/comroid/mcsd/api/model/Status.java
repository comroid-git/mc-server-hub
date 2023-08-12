package org.comroid.mcsd.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Value;
import org.comroid.api.IntegerAttribute;
import org.comroid.mcsd.util.McFormatCode;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

@Getter
public enum Status implements IntegerAttribute, IStatusMessage {
    Unknown     ("❔", McFormatCode.Dark_Gray.getColor()),
    Offline     ("❌", McFormatCode.Red.getColor()),
    Starting    ("⏯️", McFormatCode.Aqua.getColor()),
    Maintenance ("\uD83D\uDD27", McFormatCode.Yellow.getColor()),
    Backing_Up  ("\uD83D\uDCBE", McFormatCode.Green.getColor()),
    Updating    ("\uD83D\uDD04️", McFormatCode.Light_Purple.getColor()),
    In_Trouble  ("⚠️", McFormatCode.Gold.getColor()),
    Online      ("✅", McFormatCode.Dark_Green.getColor());

    private final String emoji;
    private final Color color;

    Status(String emoji, Color color) {
        this.emoji = emoji;
        this.color = color;
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
