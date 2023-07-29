package org.comroid.mcsd.util;

import lombok.*;
import org.comroid.abstr.DataNode;
import org.comroid.api.Named;
import org.comroid.api.Vector;
import org.comroid.util.JSON;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public interface Tellraw {
    @With
    @Value
    @Builder
    class Command implements Tellraw {
        ISelector selector;
        @Singular
        List<Component> components;

        @Override
        @SneakyThrows
        public String toString() {
            return "tellraw " + selector + " " + components.stream()
                    .map(Component::toString)
                    .collect(Collectors.joining(",","[","]"));
        }
    }

    interface ISelector extends Tellraw {}

    @With
    @Value
    @Builder
    class Selector implements ISelector {
        @Getter
        public enum Base implements ISelector {
            NEAREST_PLAYER("@p"),
            RANDOM_PLAYER("@r"),
            ALL_PLAYERS("@a"),
            ALL_ENTITIES("@e"),
            EXECUTOR("@s");

            private final String string;

            Base(String string) {
                this.string = string;
            }

            @Override
            @SneakyThrows
            public String toString() {
                return string;
            }
        }

        Base base;
        @Nullable Vector.N3 coordinate;
        @Nullable Double distance;
        @Nullable Vector.N3 dimensions;
        @Nullable Double Pitch;
        @Nullable Double Yaw;
        @Nullable String tag;
        @Nullable String team;
        @Nullable String name;
        @Nullable String type;
        @Nullable String predicate;
        @Nullable String nbt;
        @Nullable Double level;
        @Nullable String gamemode;
        @Nullable @Singular List<String> scores;
        @Nullable @Singular List<String> advancements;

        @Override
        @SneakyThrows
        public String toString() {
            return base.string; // todo
        }
    }

    @With
    @Value
    @Builder
    class Component implements Tellraw {
        @Nullable String text;
        @Nullable @Singular("format")
        Set<McFormatCode> format;
        @Nullable Event clickEvent;
        @Nullable Event hoverEvent;

        @Override
        @SneakyThrows
        public String toString() {
            var node = new JSON.Object();
            if (text!=null)node.set("text", text);
            if (format!=null) for (var code : format) {
                if (code.isFormat())
                    node.set(code.name().toLowerCase(), true);
                else if (code.isColor())
                    node.set("color", code.name().toLowerCase());
                else if (code.isReset()) {
                    for (var format : McFormatCode.FORMATS)
                        node.set(format.name().toLowerCase(), false);
                    node.set("color", McFormatCode.White.name().toLowerCase());
                }
            }
            if (clickEvent!=null)node.put("clickEvent", clickEvent.json());
            if (hoverEvent!=null)node.put("hoverEvent", hoverEvent.json());
            return node.toString();
        }
    }

    @With
    @Value
    @Builder
    @AllArgsConstructor
    class Event implements Tellraw {
        @NotNull Action action;
        @NotNull String value;

        public JSON.Object json() {
            var node = new JSON.Object();
            node.set("action", action.name());
            node.set("value", value);
            return node;
        }

        @Override
        @SneakyThrows
        public String toString() {
            return json().toString();
        }

        public enum Action implements Named {
            open_url,
            run_command,
            suggest_command,
            change_page,

            show_text,
            show_item,
            show_entity
        }
    }
}
