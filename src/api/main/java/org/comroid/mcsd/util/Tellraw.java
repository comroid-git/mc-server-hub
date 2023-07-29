package org.comroid.mcsd.util;

import lombok.*;
import org.comroid.abstr.DataNode;
import org.comroid.api.Named;
import org.comroid.api.Vector;
import org.comroid.util.JSON;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
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

        public JSON.Object json() {
            var json = new JSON.Object();
            if (text!=null)json.set("text", text);
            if (format!=null) for (var code : format) {
                if (code.isFormat())
                    json.set(code.name().toLowerCase(), true);
                else if (code.isColor())
                    json.set("color", code.name().toLowerCase());
                else if (code.isReset()) {
                    for (var format : McFormatCode.FORMATS)
                        json.set(format.name().toLowerCase(), false);
                    json.set("color", McFormatCode.White.name().toLowerCase());
                }
            }
            if (clickEvent!=null)json.put("clickEvent", clickEvent.json());
            if (hoverEvent!=null)json.put("hoverEvent", hoverEvent.json());
            return json;
        }

        @Override
        @SneakyThrows
        public String toString() {
            return json().toString();
        }

        public String toFullString() {
            return "[%s]".formatted(toString());
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
            var json = new JSON.Object();
            json.set("action", action.name());
            if (action != Action.show_text)
                json.set("value", value);
            else json.set("contents", Arrays.stream(value.split("\n"))
                    .map(DataNode.Value::new)
                    .collect(Collectors.toCollection(JSON.Array::new)));
            return json;
        }

        @Override
        @SneakyThrows
        public String toString() {
            return json().toString();
        }

        @SuppressWarnings("unused")
        public enum Action implements Named {
            open_url,
            run_command,
            suggest_command,
            change_page,

            show_text,
            show_item,
            show_entity;

            public Event value(String value) {
                return new Event(this, value);
            }
        }
    }
}
