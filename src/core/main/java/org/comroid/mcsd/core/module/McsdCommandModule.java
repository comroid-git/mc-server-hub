package org.comroid.mcsd.core.module;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.extern.java.Log;
import org.comroid.mcsd.core.entity.Server;

import java.util.regex.Pattern;

@Log
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class McsdCommandModule extends ServerModule {
    public static final Pattern McsdPattern = ConsoleModule.pattern("(?<username>[\\S\\w_-]+) issued server command: /mcsd (?<command>[\\w\\s_-]+)\\r?\\n?.*");
    public static final Factory<McsdCommandModule> Factory = new Factory<>(McsdCommandModule.class) {
        @Override
        public McsdCommandModule create(Server server) {
            return new McsdCommandModule(server);
        }
    };

    protected McsdCommandModule(Server server) {
        super(server);
    }
}
