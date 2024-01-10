package org.comroid.mcsd.api.dto;

import lombok.Value;
import org.comroid.api.attr.BitmaskAttribute;
import org.comroid.api.data.seri.DataNode;
import org.comroid.api.text.Markdown;

@Value
public class PlayerEvent implements DataNode {
    String username;
    String message;
    Type type;

    public String toString() {
        return type == Type.Chat ? message : Markdown.Quote.apply(message);
    }

    public enum Type implements BitmaskAttribute<Type> {
        Other, JoinLeave, Achievement, Death, Chat
    }
}
