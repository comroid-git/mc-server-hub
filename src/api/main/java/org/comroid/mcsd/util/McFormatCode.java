package org.comroid.mcsd.util;

import lombok.Getter;
import org.comroid.api.Named;
import org.comroid.api.TextDecoration;

import java.awt.*;

public enum McFormatCode implements TextDecoration, Named {
    Black           ('0', 0x000000),
    DarkBlue        ('1', 0x0000AA),
    DarkGreen       ('2', 0x00AA00),
    DarkAqua        ('3', 0x00AAAA),
    DarkRed         ('4', 0xAA0000),
    DarkPurple      ('5', 0xAA00AA),
    Gold            ('6', 0xFFAA00),
    Gray            ('7', 0xAAAAAA),
    DarkGray        ('8', 0x555555),
    Blue            ('9', 0x5555FF),
    Green           ('a', 0x55FF55),
    Aqua            ('b', 0x55FFFF),
    Red             ('c', 0xFF5555),
    LightPurple     ('d', 0xFF55FF),
    Yellow          ('e', 0xFFFF55),
    White           ('f', 0xFFFFFF),
    Obfuscated      ('k'),
    Bold            ('l'),
    Strikethrough   ('m'),
    Underline       ('n'),
    Italic          ('o'),
    Reset           ('r');

    private @Getter final String code;
    private @Getter final int hex;

    McFormatCode(char ident) {
        this(ident, 0);
    }

    McFormatCode(char ident, int hex) {
        this.code = "§"+ident;
        this.hex = hex;
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
        return name();
    }

    @Override
    public String getPrimaryName() {
        return code;
    }
}
