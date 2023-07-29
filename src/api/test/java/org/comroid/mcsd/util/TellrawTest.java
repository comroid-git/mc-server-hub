package org.comroid.mcsd.util;

import static org.comroid.mcsd.util.McFormatCode.*;

public class TellrawTest {
    public static void main(String[] args) {
        System.out.println(
                Tellraw.Command.builder()
                        .selector(Tellraw.Selector.Base.ALL_PLAYERS)
                        .component(Gray.text("<").build())
                        .component(Dark_Aqua.text("kaleidox").build())
                        .component(Gray.text(">").build())
                        .component(Reset.text(" hello world").build())
                        .build()
                        .toString()
        );
    }
}
