package org.comroid.mcsd.api.dto;

import lombok.Value;
import org.comroid.abstr.DataNode;
import org.comroid.api.BitmaskAttribute;
import org.comroid.util.Markdown;

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
