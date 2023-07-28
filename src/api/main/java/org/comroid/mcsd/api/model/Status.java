package org.comroid.mcsd.api.model;

import lombok.Getter;
import org.comroid.api.IntegerAttribute;

import java.awt.*;

@Getter
public enum Status implements IntegerAttribute {
    Unknown     ("❔", Color.BLACK),
    Offline     ("❌", Color.RED),
    Starting    ("⏯", Color.BLUE),
    Maintenance ("�", Color.MAGENTA),
    Online      ("✅", Color.GREEN);

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
