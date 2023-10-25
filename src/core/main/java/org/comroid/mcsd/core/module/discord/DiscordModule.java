package org.comroid.mcsd.core.module.discord;

import emoji4j.EmojiUtils;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.ToString;
import lombok.extern.java.Log;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import org.comroid.api.Component;
import org.comroid.api.Polyfill;
import org.comroid.api.SupplierX;
import org.comroid.api.ThrowingSupplier;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.entity.User;
import org.comroid.mcsd.core.model.DiscordMessageSource;
import org.comroid.mcsd.core.module.player.ConsolePlayerEventModule;
import org.comroid.mcsd.core.module.console.ConsoleModule;
import org.comroid.mcsd.core.module.ServerModule;
import org.comroid.mcsd.core.module.player.PlayerEventModule;
import org.comroid.mcsd.core.module.status.StatusModule;
import org.comroid.mcsd.core.repo.UserRepo;
import org.comroid.mcsd.util.Tellraw;

import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;
import static org.comroid.mcsd.util.McFormatCode.*;
import static org.comroid.mcsd.util.Tellraw.Event.Action.open_url;
import static org.comroid.mcsd.util.Tellraw.Event.Action.show_text;

@Log
@Getter
@ToString
@Component.Requires({ConsolePlayerEventModule.class,ConsoleModule.class})
public class DiscordModule extends ServerModule {
    public static final Pattern EmojiPattern = Pattern.compile(".*:(?<name>[\\w-_]+):?.*");

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
        var chat = server.component(ConsolePlayerEventModule.class).map(PlayerEventModule::getBus);
        var consoleModule = server.component(ConsoleModule.class);

        adapter.getJda().awaitReady();

        chat.ifBothPresent(consoleModule, (chatBus, console) -> {
            // public channel
            Optional.ofNullable(server.getPublicChannelId()).ifPresent(id -> {
                final var webhook = adapter.getWebhook(server.getPublicChannelWebhook(), id)
                        .thenApply(adapter::messageTemplate).join();
                final var bot = adapter.messageTemplate(id);

                // status -> dc
                server.component(StatusModule.class).map(StatusModule::getBus).ifPresent(bus ->
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
                        chatBus.filterData(msg -> msg.getType().isFlagSet(server.getPublicChannelEvents()))
                                .mapData(msg -> {
                                    var player = bean(UserRepo.class).get(msg.getUsername()).assertion();
                                    String str = msg.toString();
                                    str = EmojiPattern.matcher(str).replaceAll(match -> {
                                        var name = match.group(1);
                                        var emoji = ThrowingSupplier.fallback(()->EmojiUtils.getEmoji(name),$->null).get();
                                        if (emoji != null)
                                            return emoji.getEmoji();
                                        var results = adapter.getJda().getEmojisByName(name, true);
                                        return SupplierX.ofStream(results.stream())
                                                .map(CustomEmoji::getAsMention)
                                                .orElse(match.group(0));
                                    });
                                    return new DiscordMessageSource(str)
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
                                        .component(White.text("> ").build())
                                        // todo convert markdown to tellraw data
                                        .component(Reset.text(msg.getContentStripped() + (msg.getAttachments().isEmpty()
                                                ? ""
                                                :msg.getAttachments().stream()
                                                .map(Message.Attachment::getUrl)
                                                .collect(Collectors.joining(" ")))).build())
                                        .build()
                                        .toString())
                                .peekData(log::finest)
                                .subscribeData(tellraw -> console.execute(tellraw).exceptionally(Polyfill.exceptionLogger(log)))
                );
            });

            //moderation channel
            Optional.ofNullable(server.getModerationChannelId()).ifPresent(id -> addChildren(/*todo*/));

            // console channel
            Optional.ofNullable(server.getConsoleChannelId()).ifPresent(id -> {
                final var channel = adapter.channelAsStream(id, server.isFancyConsole());
                addChildren(
                        // mc -> dc
                        console.getBus().subscribeData(channel::println),
                        // dc -> mc
                        adapter.listenMessages(id)
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
                                .subscribeData(input -> console.execute(input).exceptionally(Polyfill.exceptionLogger(log)))
                );
            });
        });
    }
}
