package org.comroid.mcsd.core.module;

import lombok.Value;
import org.comroid.api.Event;
import org.comroid.mcsd.api.dto.ChatMessage;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.util.Utils;

import java.util.regex.Pattern;

@Value
public class ChatModule extends ServerModule {
    public static final Pattern ChatPattern = ConsoleModule.pattern(
            "([(\\[{<](?<prefix>[\\w\\s_-]+)[>}\\])]\\s?)*" +
            //"([(\\[{<]" +
            "<" +
            "(?<username>[\\w\\S_-]+)" +
            ">\\s?" +
            //"[>}\\])]\\s?)\\s?" +
            "([(\\[{<](?<suffix>[\\w\\s_-]+)[>}\\])]\\s?)*" +
            "(?<message>.+)\\r?\\n?.*");
    public static final Pattern BroadcastPattern = ConsoleModule.pattern(
            "(?<username>[\\S\\w_-]+) issued server command: " +
            "/(?<command>(me)|(say)|(broadcast)) " +
            "(?<message>.+)\\r?\\n?.*");
    public static final Pattern PlayerEventPattern = ConsoleModule.pattern(
            "(?<username>[\\S\\w_-]+) " +
            "(?<message>((joined|left) the game|has (made the advancement|completed the challenge) " +
            "(\\[(?<advancement>[\\w\\s]+)])))\\r?\\n?");
    public static final Factory<ChatModule> Factory = new Factory<>(ChatModule.class) {
        @Override
        public ChatModule create(Server server) {
            return new ChatModule(server);
        }
    };

    Event.Bus<ChatMessage> chat = new Event.Bus<>(); // todo

    private ChatModule(Server server) {
        super(server);
    }

    @Override
    protected void $initialize() {
        super.$initialize();
        var console = server.component(ConsoleModule.class)
                .orElseThrow(()->new InitFailed("No Console module is loaded"));
        Utils.listenForPattern()
    }
}
