package org.comroid.mcsd.util;

import org.comroid.api.TextDecoration;
import org.comroid.util.Markdown;

public class TextDecorationTest {
    public static void main(String[] args) {
        final var input = "**Bold is extreme** and an _italics text may be weird_, what about a _second italics_, and I hate ~~strikethrough~~";
        final var output = TextDecoration.convert(input, Markdown.class, McFormatCode.class);

        System.out.println("input = " + input);
        System.out.println("output = " + output);
    }
}
