package org.comroid.mcsd.core.module.discord;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.extern.java.Log;
import net.dv8tion.jda.api.EmbedBuilder;
import org.comroid.api.Component;
import org.comroid.api.Polyfill;
import org.comroid.mcsd.core.entity.AbstractEntity;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.entity.User;
import org.comroid.mcsd.core.model.DiscordMessageSource;
import org.comroid.mcsd.core.module.player.ConsolePlayerEventModule;
import org.comroid.mcsd.core.module.console.ConsoleModule;
import org.comroid.mcsd.core.module.ServerModule;
import org.comroid.mcsd.core.module.status.StatusModule;
import org.comroid.mcsd.core.repo.UserRepo;
import org.comroid.mcsd.util.Tellraw;

import java.time.Instant;
import java.util.Optional;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;
import static org.comroid.mcsd.util.McFormatCode.*;
import static org.comroid.mcsd.util.Tellraw.Event.Action.open_url;
import static org.comroid.mcsd.util.Tellraw.Event.Action.show_text;

@Log
@Getter
@ToString
@Component.Requires({ConsolePlayerEventModule.class,ConsoleModule.class})
public class DiscordModule extends ServerModule {
    public static final Factory<DiscordModule> Factory = new Factory<>(DiscordModule.class) {
        @Override
        public DiscordModule create(Server parent) {
            return new DiscordModule(parent);
        }
    };
    protected final DiscordAdapter adapter;

    public DiscordModule(Server parent) {
        super(parent);
        this.adapter = Optional.ofNullable(parent.getDiscordBot())
                .map(DiscordAdapter::get)
                .orElseThrow();
    }

    @Override
    @SneakyThrows
    protected void $initialize() {
        var chat = parent.component(ConsolePlayerEventModule.class).map(ConsolePlayerEventModule::getBus);
        var consoleModule = parent.component(ConsoleModule.class);

        adapter.getJda().awaitReady();

        chat.ifBothPresent(consoleModule, (chatBus, console) -> {
            // public channel
            Optional.ofNullable(parent.getPublicChannelId()).ifPresent(id -> {
                final var webhook = adapter.getWebhook(parent.getPublicChannelWebhook(), id)
                        .thenApply(adapter::messageTemplate).join();
                final var bot = adapter.messageTemplate(id);

                // status -> dc
                parent.component(StatusModule.class).map(StatusModule::getBus).ifPresent(bus ->
                        addChildren(bus.mapData(msg -> new EmbedBuilder()
                                        //.setAuthor(parent.getAlternateName(),
                                        //        Optional.ofNullable(parent.getHomepage())
                                        //                .orElse(parent.getViewURL()),
                                        //        parent.getThumbnailURL())
                                        .setDescription(msg.toStatusMessage())
                                        .setColor(msg.getStatus().getColor())
                                        .setFooter(msg.getMessage())
                                        .setTimestamp(Instant.now()))
                                .mapData(DiscordMessageSource::new)
                                .peekData(msg -> msg.setAppend(false))
                                .subscribeData(bot)));

                addChildren(
                        // mc -> dc
                        chatBus.filterData(msg -> msg.getType().isFlagSet(parent.getPublicChannelEvents()))
                                .mapData(msg -> {
                                    var player = bean(UserRepo.class).get(msg.getUsername()).assertion();
                                    return new DiscordMessageSource(msg.toString())
                                            .setDisplayUser(player.getDisplayUser(User.DisplayUser.Type.Discord, User.DisplayUser.Type.Minecraft)
                                                    .assertion())
                                            .setAppend(true);
                                })
                                .subscribeData(webhook),
                        // dc -> mc
                        adapter.listenMessages(id)
                                .filterData(msg -> !msg.getAuthor().isBot() && !msg.getContentRaw().isBlank())
                                .mapData(msg -> Tellraw.Command.builder()
                                        .selector(Tellraw.Selector.Base.ALL_PLAYERS)
                                        .component(White.text("<").build())
                                        .component(Dark_Aqua.text(bean(UserRepo.class)
                                                        .findByDiscordId(msg.getAuthor().getIdLong())
                                                        // prefer effective name here; only try minecraft variant now
                                                        .flatMap(usr->usr.getDisplayUser(User.DisplayUser.Type.Minecraft).wrap())
                                                        .map(User.DisplayUser::username)
                                                        // otherwise use effective name
                                                        .orElseGet(() -> msg.getAuthor().getEffectiveName()))
                                                .hoverEvent(show_text.value("Open in Discord"))
                                                .clickEvent(open_url.value(msg.getJumpUrl()))
                                                .format(Underlined)
                                                .build())
                                        .component(White.text(">").build())
                                        // todo convert markdown to tellraw data
                                        .component(Reset.text(" " + msg.getContentStripped()).build())
                                        .build()
                                        .toString())
                                .peekData(log::fine)
                                .subscribeData(tellraw -> console.execute(tellraw).exceptionally(Polyfill.exceptionLogger(log)))
                );
            });

            //moderation channel
            Optional.ofNullable(parent.getModerationChannelId()).ifPresent(id -> addChildren(/*todo*/));

            // console channel
            Optional.ofNullable(parent.getConsoleChannelId()).ifPresent(id -> {
                final var channel = adapter.channelAsStream(id, parent.isFancyConsole());
                addChildren(
                        // mc -> dc
                        console.getBus().subscribeData(channel::println),
                        // dc -> mc
                        adapter.listenMessages(id)
                                .filterData(msg -> !msg.getAuthor().isBot())
                                .mapData(msg -> {
                                    var raw = msg.getContentRaw();
                                    if (parent.isFancyConsole() && !msg.getAuthor().equals(adapter.getJda().getSelfUser()))
                                        msg.delete().queue();
                                    //noinspection RedundantCast //ide error
                                    return (String) raw;
                                })
                                .filterData(cmd -> parent.getConsoleChannelPrefix() == null || cmd.startsWith(parent.getConsoleChannelPrefix()))
                                .mapData(cmd -> parent.getConsoleChannelPrefix() == null ? cmd : cmd.substring(parent.getConsoleChannelPrefix().length()))
                                .subscribeData(input -> console.execute(input).exceptionally(Polyfill.exceptionLogger(log)))
                );
            });
        });
    }
}
