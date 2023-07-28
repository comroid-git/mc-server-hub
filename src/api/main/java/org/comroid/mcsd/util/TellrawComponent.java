package org.comroid.mcsd.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.util.StdConverter;
import lombok.*;
import lombok.experimental.WithBy;
import org.comroid.api.Named;

@With
@Value
@WithBy
@Builder
public class TellrawComponent {
    String text;
    McFormatCode color;
    boolean bold;
    boolean italic;
    boolean underlined;
    boolean strikethrough;
    boolean obfuscated;
    Event clickEvent;
    Event hoverEvent;

    @SneakyThrows
    public String toString() {
        return new ObjectMapper().writeValueAsString(this);
    }

    @With
    @Value
    @WithBy
    @lombok.Builder
    @AllArgsConstructor
    public static class Event {
        Action action;
        String value;

        @SneakyThrows
        public String toString() {
            return new ObjectMapper().writeValueAsString(this);
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
