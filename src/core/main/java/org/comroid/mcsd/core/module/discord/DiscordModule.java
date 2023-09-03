package org.comroid.mcsd.core.module.discord;

import lombok.extern.java.Log;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.module.ChatModule;
import org.comroid.mcsd.core.module.ConsoleModule;
import org.comroid.mcsd.core.module.ServerModule;
import org.comroid.mcsd.core.module.StatusModule;
import org.comroid.mcsd.core.repo.MinecraftProfileRepo;
import org.comroid.mcsd.util.Tellraw;

import java.time.Instant;
import java.util.Optional;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;
import static org.comroid.mcsd.util.McFormatCode.*;
import static org.comroid.mcsd.util.Tellraw.Event.Action.open_url;
import static org.comroid.mcsd.util.Tellraw.Event.Action.show_text;

@Log
public class DiscordModule extends ServerModule {
    public static final Factory<DiscordModule> Factory = new Factory<>(DiscordModule.class) {
        @Override
        public DiscordModule create(Server server) {
            return new DiscordModule(server);
        }
    };
    protected final DiscordAdapter adapter;

    public DiscordModule(Server server) {
        super(server);
        this.adapter = Optional.ofNullable(server.getDiscordBot())
                .map(DiscordAdapter::get)
                .orElseThrow();
    }

    @Override
    protected void $initialize() {
        super.$initialize();

        var chat = server.component(ChatModule.class).map(ChatModule::getChat);
        var consoleModule = server.component(ConsoleModule.class);

        chat.ifBothPresent(consoleModule, (chatBus, console) -> {
            // chat mirror
            Optional.ofNullable(server.getPublicChannelId()).ifPresent(id -> {
                final var webhook = adapter.messageTemplate(adapter.getWebhook(id));

                // status -> dc
                server.component(StatusModule.class).map(StatusModule::getBus).ifPresent(bus ->
                        addChildren(bus.mapData(msg -> new EmbedBuilder()
                                        .setAuthor(null,
                                                Optional.ofNullable(server.getHomepage())
                                                        .orElse(server.getViewURL()),
                                                server.getThumbnailURL())
                                        .setTimestamp(Instant.now()))
                                .mapData(DiscordMessageSource::new)
                                .subscribeData(webhook)));

                addChildren(
                        // mc -> dc
                        chatBus.mapData(msg -> {
                                    var player = bean(MinecraftProfileRepo.class).get(msg.getUsername());
                                    return new DiscordMessageSource(msg.toString()).setPlayer(player);
                                })
                                .subscribeData(webhook),
                        // dc -> mc
                        adapter.flatMap(MessageReceivedEvent.class)
                                .filterData(e -> e.getChannel().getIdLong() == id)
                                .mapData(MessageReceivedEvent::getMessage)
                                .filterData(msg -> !msg.getAuthor().isBot())
                                .mapData(msg -> Tellraw.Command.builder()
                                        .selector(Tellraw.Selector.Base.ALL_PLAYERS)
                                        .component(Gray.text("<").build())
                                        .component(Dark_Aqua.text(msg.getAuthor().getEffectiveName())
                                                .hoverEvent(show_text.value("Open in Discord"))
                                                .clickEvent(open_url.value(msg.getJumpUrl()))
                                                .build())
                                        .component(Gray.text(">").build())
                                        // todo convert markdown to tellraw data
                                        .component(Reset.text(" " + msg.getContentStripped()).build())
                                        .build()
                                        .toString())
                                .peekData(log::fine)
                                .subscribeData(tellraw -> console.execute(tellraw).join())
                );
            });

            //moderation channel
            Optional.ofNullable(server.getModerationChannelId()).ifPresent(id -> addChildren(/*todo*/));

            // console channel
            Optional.ofNullable(server.getConsoleChannelId()).ifPresent(id -> addChildren(
                    // mc -> dc
                    console.getConsole().subscribeData(adapter.channelAsStream(id, server.isFancyConsole())::println),
                    // dc -> mc
                    adapter.flatMap(MessageReceivedEvent.class)
                            .filterData(e -> e.getChannel().getIdLong() == id)
                            .mapData(MessageReceivedEvent::getMessage)
                            .filterData(msg -> !msg.getAuthor().isBot())
                            .mapData(msg -> {
                                var raw = msg.getContentRaw();
                                if (server.isFancyConsole() && !msg.getAuthor().equals(adapter.getJda().getSelfUser()))
                                    msg.delete().queue();
                                //noinspection RedundantCast //ide error
                                return (String) raw;
                            })
                            .filterData(cmd -> server.getConsoleChannelPrefix() == null || cmd.startsWith(server.getConsoleChannelPrefix()))
                            .mapData(cmd -> server.getConsoleChannelPrefix() == null ? cmd : cmd.substring(server.getConsoleChannelPrefix().length()))
                            .subscribeData(input -> console.execute(input).join())
            ));
        });
    }
}
