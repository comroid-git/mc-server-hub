package org.comroid.mcsd.api.model;

import lombok.Getter;
import org.comroid.api.IntegerAttribute;
import org.comroid.mcsd.util.McFormatCode;

import java.awt.*;

@Getter
public enum Status implements IntegerAttribute {
    Unknown     ("❔", McFormatCode.Dark_Gray.getColor()),
    Offline     ("❌", McFormatCode.Red.getColor()),
    Starting    ("⏯", McFormatCode.Aqua.getColor()),
    Maintenance ("�", McFormatCode.Yellow.getColor()),
    Online      ("✅", McFormatCode.Dark_Green.getColor());

    private final String emoji;
    private final Color color;

    Status(String emoji, Color color) {
        this.emoji = emoji;
        this.color = color;
    }

    @Override
    public String getAlternateName() {
        return getEmoji();
    }
}
