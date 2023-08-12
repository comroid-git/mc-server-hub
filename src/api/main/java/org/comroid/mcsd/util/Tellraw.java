package org.comroid.mcsd.util;

import lombok.*;
import lombok.experimental.NonFinal;
import org.comroid.abstr.DataNode;
import org.comroid.api.Named;
import org.comroid.api.StringAttribute;
import org.comroid.api.TextDecoration;
import org.comroid.api.Vector;
import org.comroid.util.JSON;
import org.comroid.util.Markdown;
import org.comroid.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface Tellraw {
    @With
    @Value
    @Builder
    class Command implements Tellraw {
        ISelector selector;
        @Singular
        @NonFinal
        List<Component> components;

        @Override
        @SneakyThrows
        public String toString() {
            return "tellraw " + selector + " " + components.stream()
                    .map(Component::toString)
                    .collect(Collectors.joining(",","[","]"));
        }

        public Command appendMarkdown(String txt) {
            final var map = TextDecoration.styles(Markdown.class, McFormatCode.class);
            return append(TextDecoration.replacers(Markdown.class, McFormatCode.class)
                    .map(Pair::getFirst)
                    .map(".*%s.*"::formatted)
                    .map(Pattern::compile)
                    .map(pattern -> pattern.matcher(txt))
                    .filter(Matcher::matches)
                    .flatMap(matcher -> {
                        final var ls = new ArrayList<Component>();
                        var ignored = matcher.replaceAll(result -> {
                            var format = StringAttribute.valueOf(result.group(1), Markdown.class)
                                    .orElse(Markdown.None);
                            var msg = result.group(2);
                            ls.add(Tellraw.Component.builder()
                                    .format(map.get(format))
                                    .text(msg)
                                    .build());
                            return msg;
                        });
                        return ls.stream();
                    })
                    .toArray(Tellraw.Component[]::new));
        }

        public Command append(Component... components) {
            this.components = Stream.concat(this.components.stream(), Stream.of(components)).toList();
            return this;
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
