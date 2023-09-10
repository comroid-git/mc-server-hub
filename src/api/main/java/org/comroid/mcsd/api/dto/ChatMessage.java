package org.comroid.mcsd.api.dto;

import lombok.Value;
import org.comroid.util.Markdown;

@Value
public class ChatMessage {
    String username;
    String message;
    boolean event;

    public String toString() {
        return event ? Markdown.Quote.apply(message) : message;
    }
}
