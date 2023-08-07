package org.comroid.mcsd.util;

import lombok.Getter;
import org.comroid.api.Named;
import org.comroid.api.TextDecoration;
import org.comroid.util.Markdown;

import java.awt.*;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum McFormatCode implements TextDecoration, Named {
    Black('0', 0x000000),
    Dark_Blue('1', 0x0000AA),
    Dark_Green('2', 0x00AA00),
    Dark_Aqua('3', 0x00AAAA),
    Dark_Red('4', 0xAA0000),
    Dark_Purple('5', 0xAA00AA),
    Gold('6', 0xFFAA00),
    Gray('7', 0xAAAAAA),
    Dark_Gray('8', 0x555555),
    Blue('9', 0x5555FF),
    Green('a', 0x55FF55),
    Aqua('b', 0x55FFFF),
    Red('c', 0xFF5555),
    Light_Purple('d', 0xFF55FF),
    Yellow('e', 0xFFFF55),
    White('f', 0xFFFFFF),

    Obfuscated('k'),
    Bold('l'),
    Strikethrough('m'),
    Underlined('n'),
    Italic('o'),

    Reset('r');

    public static final Set<McFormatCode> FORMATS = Arrays.stream(values())
            .filter(McFormatCode::isFormat)
            .collect(Collectors.toUnmodifiableSet());
    public static final Set<McFormatCode> COLORS = Arrays.stream(values())
            .filter(McFormatCode::isColor)
            .collect(Collectors.toUnmodifiableSet());

    @Getter private final String code;
    @Getter private final int hex;

    public boolean isReset() {
        return Reset == this;
    }

    public boolean isColor() {
        return hex != 0xFFFF_FFFF;
    }

    public boolean isFormat() {
        return !isColor() && !isReset();
    }

    McFormatCode(char ident) {
        this(ident, 0xFFFF_FFFF);
    }

    McFormatCode(char ident, int hex) {
        this.code = "§" + ident;
        this.hex = hex;
    }

    public Tellraw.Component.Builder text(String text) {
        return Tellraw.Component.builder()
                .text(text)
                .format(this);
    }

    public Color getColor() {
        return new Color(hex);
    }

    @Override
    public CharSequence getPrefix() {
        return code;
    }

    @Override
    public CharSequence getSuffix() {
        return Reset.getPrefix();
    }

    @Override
    public String getName() {
        return name().toLowerCase();
    }

    @Override
    public String getPrimaryName() {
        return code;
    }
}
