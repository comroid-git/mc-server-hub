package org.comroid.mcsd.core.module.discord;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.receive.ReadonlyMessage;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import net.dv8tion.jda.api.utils.Compression;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.dv8tion.jda.api.utils.MarkdownUtil;
import org.comroid.api.Polyfill;
import org.comroid.api.func.OptionalFunction;
import org.comroid.api.func.util.*;
import org.comroid.api.text.Markdown;
import org.comroid.api.text.TextDecoration;
import org.comroid.mcsd.api.Defaults;
import org.comroid.mcsd.core.MCSD;
import org.comroid.mcsd.core.entity.server.Server;
import org.comroid.mcsd.core.entity.system.DiscordBot;
import org.comroid.mcsd.core.model.DiscordMessageSource;
import org.comroid.mcsd.core.module.console.ConsoleModule;
import org.comroid.mcsd.core.module.status.BackupModule;
import org.comroid.mcsd.core.module.status.UpdateModule;
import org.comroid.mcsd.core.repo.server.ServerRepo;
import org.comroid.mcsd.core.repo.system.UserRepo;
import org.comroid.mcsd.util.McFormatCode;
import org.comroid.mcsd.util.Tellraw;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.PrintStream;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.dv8tion.jda.api.entities.Message.MAX_CONTENT_LENGTH;
import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Log
@Data
public class DiscordAdapter extends Event.Bus<GenericEvent> implements EventListener, Command.Handler {
    private static final Map<UUID, DiscordAdapter> adapters = new ConcurrentHashMap<>();
    public static final int MaxBulkDelete = 100;
    public static final int MaxEditBacklog = 15;
    private final JDA jda;

    @Contract("null -> null; _ -> _")
    public static @Nullable DiscordAdapter get(final @Nullable DiscordBot bot) {
        return bot == null ? null : adapters.computeIfAbsent(bot.getId(), $ -> new DiscordAdapter(bot));
    }

    @SneakyThrows
    public DiscordAdapter(DiscordBot bot) {
        this.jda = JDABuilder.createDefault(bot.getToken(), GatewayIntent.getIntents(GatewayIntent.ALL_INTENTS))
                .setActivity(Activity.playing("Minecraft"))
                .setCompression(Compression.ZLIB)
                .addEventListeners(this)
                .build();

        /*if (!Debug.isDebug())
            jda.retrieveCommands().submit()
                    /* todo: must not delete all commands every time
                    .thenCompose(ls -> CompletableFuture.allOf(ls.stream()
                            .map(net.dv8tion.jda.api.interactions.commands.Command::delete)
                            .map(RestAction::submit)
                            .toArray(CompletableFuture[]::new)))
                    *//*
                    .thenCompose($ -> jda.updateCommands().addCommands(
                            Commands.slash("info", "Shows parent information")
                                    .setGuildOnly(true),
                            Commands.slash("list", "Shows list of online players")
                                    .setGuildOnly(true),
                            Commands.slash("whois", "Check the profile of a user")
                                    .addOption(OptionType.STRING, "minecraft", "Minecraft Username", false)
                                    .addOption(OptionType.USER, "discord", "Discord User", false)
                                    .setGuildOnly(true),
                            Commands.slash("link", "Link your Minecraft account. You will be sent a code in-game")
                                    .addOption(OptionType.STRING, "username", "Minecraft Username", true)
                                    .setGuildOnly(true),
                            Commands.slash("verify", "Verify Minecraft Account linkage. Used after running link command")
                                    .addOption(OptionType.STRING, "code", "Your verification code", true)
                                    .setGuildOnly(true),
                            Commands.slash("backup", "Create a backup of the parent")
                                    .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_PERMISSIONS))
                                    .setGuildOnly(true),
                            Commands.slash("update", "Update the parent")
                                    .addOption(OptionType.BOOLEAN, "force", "Force the Update")
                                    .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_PERMISSIONS))
                                    .setGuildOnly(true),
                            Commands.slash("execute", "Run a command on the parent")
                                    .addOption(OptionType.STRING, "command", "The command to run", true)
                                    .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_PERMISSIONS))
                                    .setGuildOnly(true)).submit())
                    .join();*/
        final var cmdr = bean(Command.Manager.class);
        if (cmdr.getAdapters().stream().noneMatch(Command.Manager.Adapter$JDA.class::isInstance)) {
            cmdr.new Adapter$JDA(jda);
            cmdr.register(this);
        }
        //flatMap(SlashCommandInteractionEvent.class).subscribeData(e -> cmdr.execute(e.getName(), e));
    }

    @Override
    public void handleResponse(Command.Delegate cmd, @NotNull Object response, Object... args) {
        final var e = Stream.of(args)
                .flatMap(Streams.cast(SlashCommandInteractionEvent.class))
                .findAny()
                .orElseThrow();
        final var user = bean(UserRepo.class)
                .findByDiscordId(e.getUser().getIdLong())
                .orElse(null);
        if (response instanceof CompletableFuture)
            e.deferReply().setEphemeral(cmd.ephemeral())
                    .submit()
                    .thenCombine(((CompletableFuture<?>) response), (hook, resp) -> {
                        WebhookMessageCreateAction<Message> req;
                        if (resp instanceof EmbedBuilder)
                            req = hook.sendMessageEmbeds(embed((EmbedBuilder) resp, user).build());
                        else req = hook.sendMessage(String.valueOf(resp));
                        return req.submit();
                    })
                    .thenCompose(Function.identity())
                    .exceptionally(Polyfill.exceptionLogger());
        else {
            ReplyCallbackAction req;
            if (response instanceof EmbedBuilder)
                req = e.replyEmbeds(embed((EmbedBuilder) response, user).build());
            else req = e.reply(String.valueOf(response));
            req.setEphemeral(cmd.ephemeral()).submit();
        }
    }

    @Override
    public @Nullable String handleThrowable(Throwable throwable) {
        return Markdown.CodeBlock.apply(Command.Handler.super.handleThrowable(throwable));
    }

    @Command
    public CompletableFuture<EmbedBuilder> info(MessageChannelUnion channel) {
        var parent = bean(ServerRepo.class).findByDiscordChannel(channel.getIdLong())
                .orElseThrow(() -> new Command.Error("Unable to find parent"));
        return parent.status().thenApply(stat -> {
            var embed = new EmbedBuilder()
                    .setTitle(stat.getStatus().toStatusMessage(), parent.getHomepage())
                    .setDescription(TextDecoration.sanitize(stat.getMotd(), McFormatCode.class, Markdown.class))
                    .setColor(stat.getStatus().getColor())
                    .setThumbnail(parent.getThumbnailURL())
                    .addField("Host", parent.getHost(), true)
                    .addField("Version", parent.getMcVersion(), true)
                    .addField("Game Type", parent.getMode().getName(), true)
                    .setTimestamp(stat.getTimestamp());
            if (stat.getPlayers() != null)
                if (!stat.getPlayers().isEmpty())
                    embed.addField("Players", "- " + String.join("\n- ", stat.getPlayers()), false);
                else embed.addField("Players", "There are no players online", false);
            else
                embed.addField("Players", "%d out of %d".formatted(stat.getPlayerCount(), parent.getMaxPlayers()), false);
            Optional.ofNullable(parent.getOwner())
                    .ifPresent(owner -> embed.setAuthor("Owner: " + owner.getName(), owner.getNameMcURL(), owner.getHeadURL()));
            return embed;
        });
    }

    @Command
    public CompletableFuture<EmbedBuilder> list(MessageChannelUnion channel) {
        var parent = bean(ServerRepo.class).findByDiscordChannel(channel.getIdLong())
                .orElseThrow(() -> new Command.Error("Unable to find parent"));
        return parent.status().thenApply(stat -> {
            var embed = new EmbedBuilder()
                    .setDescription(TextDecoration.sanitize(stat.getMotd(), McFormatCode.class, Markdown.class))
                    .setThumbnail(parent.getThumbnailURL())
                    .setTimestamp(stat.getTimestamp());
            if (stat.getPlayers() == null)
                return embed.setDescription("There are no players online");
            final var users = bean(UserRepo.class);
            stat.getPlayers().forEach(playerName -> embed.addField(
                    playerName,
                    users.findByMinecraftName(playerName).map(user -> user.getDiscordId() == null
                            ? Markdown.Italic.apply("No linked Discord account (wip)")
                            : jda.retrieveUserById(user.getDiscordId())
                            .map(User::getEffectiveName)
                            .submit().join()).orElseThrow(),
                    true));
            return embed;
        });
    }

    @Command(ephemeral = true)
    public CompletableFuture<String> whois(MessageChannelUnion channel,
                                           @Command.Arg(required = false) @Nullable String minecraftUsername,
                                           @Command.Arg(required = false) @Nullable User discordUser) {
        final var users = bean(UserRepo.class);

        if (minecraftUsername == null && discordUser == null)
            return CompletableFuture.completedFuture("Please provide a Minecraft or Discord user");
        else if (minecraftUsername != null)
            //noinspection DataFlowIssue
            return users.get(minecraftUsername)
                    .filter(data -> data.getDiscordId() != null)
                    .map(data -> jda.retrieveUserById(data.getDiscordId())
                            .useCache(true)
                            .map(usr -> minecraftUsername + " is " + usr.getAsMention() + " on Discord")
                            .submit())
                    .orElseGet(() -> CompletableFuture.completedFuture(minecraftUsername + " has not linked their Accounts"));
        else {
            final long dcid = discordUser.getIdLong();
            return users.findByDiscordId(dcid)
                    .filter(user -> user.getMinecraftId() != null)
                    .map(user -> user.getMinecraftName().thenApply(mc->discordUser.getAsMention() + " is " + mc + " in Minecraft") )
                    .orElseGet(() -> CompletableFuture.completedFuture(discordUser.getAsMention() + " has not linked their Accounts"));
        }
    }

    @Command(ephemeral = true)
    public String link(User cmdUser, @Command.Arg String minecraftUsername) {
        final var profiles = bean(UserRepo.class);
        var profile = profiles.get(minecraftUsername)
                .orElseThrow(() -> new Command.Error("Invalid username"));
        var code = profiles.startMcDcLinkage(profile);
        final var cmd = Tellraw.notify(minecraftUsername, McFormatCode.Blue.text("Account Verification").build(),
                        "Use code ")
                .component(McFormatCode.Aqua.text(code).build())
                .component(McFormatCode.Reset.text(" to link this Minecraft Account to Discord User ").build())
                .component(McFormatCode.Aqua.text(cmdUser.getEffectiveName()).build())
                .build()
                .toString();
        // todo: should check if player is online
        ((List<Server>)bean(List.class, "servers")).stream()
                .flatMap(srv -> srv.component(ConsoleModule.class).stream())
                .forEach(console -> console.execute(cmd));
        return "Please check Minecraft Chat for the code and then run /verify <code>\n" +
                "The code runs out in 15 Minutes";
    }

    @Command(ephemeral = true)
    public String verify(User cmdUser, @Command.Arg String code) {
        final var users = bean(UserRepo.class);
        final var profile = users.findByVerification(code)
                .orElseThrow(() -> new Command.Error("Invalid code"));
        // if a timeout exists that is before now(), throw
        if (Optional.ofNullable(profile.getVerificationTimeout())
                .filter(x->x.isBefore(Instant.now()))
                .isPresent()) {
            users.clearVerification(profile.getId());
            throw new Command.Error("Verification timeout");
        }
        final var user = users.merge(users.findByDiscordId(cmdUser.getIdLong()), Optional.of(profile));
        users.clearVerification(user.getId());
        return "Minecraft account " + profile.getMinecraftName().join() + " has been linked";
    }

    @Command(ephemeral = true)
    public CompletableFuture<String> backup(MessageChannelUnion channel) {
        return bean(ServerRepo.class).findByDiscordChannel(channel.getIdLong())
                .flatMap(srv -> srv.component(BackupModule.class).wrap())
                .orElseThrow(() -> new Command.Error("Unable to find parent"))
                .runBackup(true)
                .thenApply($->"Backup complete");
    }

    @Command(ephemeral = true)
    public CompletableFuture<String> update(MessageChannelUnion channel,
                                            @Command.Arg(required = false) @Nullable Boolean force) {
        return bean(ServerRepo.class).findByDiscordChannel(channel.getIdLong())
                .flatMap(srv -> srv.component(UpdateModule.class).wrap())
                .orElseThrow(() -> new Command.Error("Unable to find parent"))
                .runUpdate(Optional.ofNullable(force).orElse(false))
                .thenApply($->$?"Update complete":"Update skipped");
    }

    @Command(ephemeral = true)
    public String execute(MessageChannelUnion channel, @Command.Arg String command) {
        var console = bean(ServerRepo.class).findByDiscordChannel(channel.getIdLong())
                .flatMap(srv -> srv.component(ConsoleModule.class).wrap())
                .orElseThrow(() -> new Command.Error("Unable to find parent"));
        console.execute(command);
        return "Command was sent";
    }

    @Override
    public void onEvent(@NotNull GenericEvent event) {
        publish(null, event);
    }

    public Optional<String> createWebhook(long channelId) {
        return Optional.ofNullable(jda.getTextChannelById(channelId))
                .map(chl -> chl.createWebhook("MCSD Chat Relay")
                        .submit()
                        .thenApply(Webhook::getUrl)
                        .join());
    }

    public MessagePublisher messageTemplate(final WebhookClient sender) {
        return new MessagePublisher() {
            private final CompletableFuture<Webhook> webhook = jda
                    .retrieveWebhookById(sender.getId())
                    .submit();

            @Override
            protected CompletableFuture<@NotNull Long> channelId() {
                return webhook.thenApply(Webhook::getChannel).thenApply(ISnowflake::getIdLong);
            }

            @Override
            protected CompletableFuture<@NotNull String> defaultAuthorName() {
                return webhook.thenApply(Webhook::getName);
            }

            @Override
            public CompletableFuture<@NotNull Long> edit(@NotNull Long messageId, DiscordMessageSource msg) {
                return msg.execEdit(messageId, this::edit, this::edit).thenApply(ReadonlyMessage::getId);
            }

            private @NotNull CompletableFuture<ReadonlyMessage> edit(@NotNull Long messageId, String text) {
                return sender.edit(messageId, text);
            }

            private @NotNull CompletableFuture<ReadonlyMessage> edit(@NotNull Long messageId, EmbedBuilder embed) {
                return sender.edit(messageId, WebhookEmbedBuilder.fromJDA(embed.build()).build());
            }

            @Override
            public CompletableFuture<@NotNull Long> send(final DiscordMessageSource msg) {
                return msg.execSend(text -> send(msg, text), this::send);
            }

            private CompletableFuture<@NotNull Long> send(DiscordMessageSource msg, String text) {
                var player = msg.getDisplayUser();
                return sender.send(whMsg()
                                .setContent(text)
                                .setAvatarUrl(player != null ? player.avatarUrl() : null)
                                .setUsername(player != null ? player.username() : null)
                                .build())
                        .thenApply(ReadonlyMessage::getId);
            }

            private CompletableFuture<@NotNull Long> send(EmbedBuilder embed) {
                return sender.send(whMsg()
                                .addEmbeds(WebhookEmbedBuilder.fromJDA(embed.build())
                                        .build()).build())
                        .thenApply(ReadonlyMessage::getId);
            }

            private WebhookMessageBuilder whMsg() {
                return new WebhookMessageBuilder();
            }
        };
    }

    public MessagePublisher messageTemplate(final long channelId) {
        return new MessagePublisher() {
            private final @NotNull TextChannel channel = Objects.requireNonNull(jda.getTextChannelById(channelId),
                    "Channel " + channelId + " not found");

            @Override
            protected CompletableFuture<@NotNull Long> channelId() {
                return CompletableFuture.completedFuture(channelId);
            }

            @Override
            protected CompletableFuture<@NotNull String> defaultAuthorName() {
                return CompletableFuture.completedFuture(jda.getSelfUser().getEffectiveName());
            }

            @Override
            public CompletableFuture<@NotNull Long> edit(@NotNull Long messageId, DiscordMessageSource msg) {
                return msg.execEdit(messageId, this::edit, this::edit);
            }

            private CompletableFuture<@NotNull Long> edit(@NotNull Long messageId, String text) {
                return channel.editMessageById(messageId, text)
                        .map(ISnowflake::getIdLong)
                        .submit();
            }

            private CompletableFuture<@NotNull Long> edit(@NotNull Long messageId, EmbedBuilder embed) {
                return channel.editMessageEmbedsById(messageId, embed.build())
                        .map(ISnowflake::getIdLong)
                        .submit();
            }

            @Override
            public CompletableFuture<@NotNull Long> send(DiscordMessageSource msg) {
                return msg.execSend(this::send, this::send);
            }

            private CompletableFuture<@NotNull Long> send(String text) {
                return channel.sendMessage(text)
                        .map(ISnowflake::getIdLong)
                        .submit();
            }

            private CompletableFuture<@NotNull Long> send(EmbedBuilder embed) {
                return channel.sendMessageEmbeds(embed.build())
                        .map(ISnowflake::getIdLong)
                        .submit();
            }
        };
    }

    public CompletableFuture<WebhookClient> getWebhook(@Nullable String webhookUrl, long channelId) {
        return CompletableFuture.supplyAsync(() -> Objects.requireNonNull(webhookUrl))
                .thenCompose(url -> {
                    final var client = WebhookClient.withUrl(url);
                    return jda.retrieveWebhookById(client.getId())
                            .submit()
                            .thenCompose(wh -> {
                                if (wh.getChannel().getIdLong() == channelId)
                                    return CompletableFuture.completedFuture(wh);
                                log.warning("Webhook " + wh.getIdLong() + " pointing to incorrect channel, recreating...");
                                return wh.delete().submit().thenApply($ -> {
                                    throw new RuntimeException("Invalid webhook, recreating");
                                });
                            })
                            .thenApply($ -> client);
                })
                .exceptionallyCompose(t -> Objects.requireNonNull(jda.getTextChannelById(channelId))
                        .createWebhook(Defaults.WebhookName)
                        .map(wh -> WebhookClientBuilder.fromJDA(wh).build())
                        .submit()
                        .thenApply(wh -> {
                            var moduleRepo = bean(MCSD.class).getModules_discord();
                            bean(ServerRepo.class)
                                    .findByDiscordChannel(channelId)
                                    .stream()
                                    .flatMap(server -> moduleRepo
                                            .findByServerIdAndDtype(server.getId(), "Discord")
                                            .stream())
                                    .map(discord -> discord.setPublicChannelWebhook(wh.getUrl()))
                                    .forEach(moduleRepo::save);
                            return wh;
                        }));
    }

    public Event.Bus<Message> listenMessages(long id) {
        return flatMap(MessageReceivedEvent.class)
                .filterData(e -> e.getChannel().getIdLong() == id)
                .mapData(MessageReceivedEvent::getMessage);
    }

    public abstract class MessagePublisher implements Consumer<DiscordMessageSource>, DiscordMessageSource.Sender {
        private final AtomicLong lastKnownMessage = new AtomicLong(0);

        protected abstract CompletableFuture<@NotNull Long> channelId();
        protected abstract CompletableFuture<@NotNull String> defaultAuthorName();

        public abstract CompletableFuture<@NotNull Long> edit(@NotNull Long messageId, DiscordMessageSource msg);
        public abstract CompletableFuture<@NotNull Long> send(DiscordMessageSource msg);

        private CompletableFuture<@NotNull Long> editOrSend(
                final DiscordMessageSource msg,
                final BiFunction<@NotNull Long, DiscordMessageSource, CompletableFuture<@NotNull Long>> edit,
                final Function<DiscordMessageSource, CompletableFuture<@NotNull Long>> send) {
            var action = related().thenCompose(editMsg -> {
                if (editMsg != null && !msg.isAppend())
                    return edit.apply(editMsg, msg);
                return send.apply(msg);
            });
            if (!msg.isAppend())
                action.thenAccept(lastKnownMessage::set);
            return action;
        }

        private CompletableFuture<@Nullable Long> related() {
            return lastKnownMessage.get() != 0 ? CompletableFuture.completedFuture(lastKnownMessage.get()) : channelId()
                    .thenApply(jda::getTextChannelById)
                    .thenCombine(defaultAuthorName(), (chl, name) -> chl.getIterableHistory().stream()
                            .limit(MaxEditBacklog)
                            .filter(msg -> msg.getAuthor().getEffectiveName().equals(name))
                            .filter(msg -> msg.getReferencedMessage() == null)
                            .findAny()
                            .orElse(null))
                    .thenApply((OptionalFunction<Message, Long>)Message::getIdLong);
        }

        @Override
        public CompletableFuture<@NotNull Long> sendString(DiscordMessageSource string) {
            return editOrSend(string, this::edit, this::send);
        }

        @Override
        public CompletableFuture<@NotNull Long> sendEmbed(DiscordMessageSource embed) {
            return editOrSend(embed, this::edit, this::send);
        }

        @Override
        public void accept(DiscordMessageSource msg) {
            // todo: chat webhook cannot access mc player anymore
            msg.send(this);
        }
    }

    private EmbedBuilder embed(@NotNull EmbedBuilder builder, @Nullable org.comroid.mcsd.core.entity.system.User player) {
        if (player != null)
            builder = builder.setAuthor(player.getName(), player.getNameMcURL(), player.getHeadURL());
        builder.setTimestamp(Instant.now());
        return builder;
    }

    public PrintStream channelAsStream(final long id, final boolean fancy) {
        final var channel = jda.getTextChannelById(id);
        if (channel == null)
            throw new NullPointerException("channel not found: " + id);
        return new PrintStream(new DelegateStream.Output(new Consumer<>() {
            public static final int MaxLength = MAX_CONTENT_LENGTH - 6;

            private final AtomicReference<CompletableFuture<Message>> msg;

            {
                // getOrCreate msg
                final var msg = Optional.of(0L)
                        .filter($ -> fancy)
                        .flatMap($ -> Streams.of(channel.getIterableHistory())
                                .filter(m -> jda.getSelfUser().equals(m.getAuthor()))
                                .findFirst())
                        .map(CompletableFuture::completedFuture)
                        .orElseGet(() -> newMsg(null).submit());
                this.msg = new AtomicReference<>(msg);

                // cleanup channel
                if (fancy)
                    msg.thenApply(ISnowflake::getIdLong).thenComposeAsync(it -> Polyfill
                            .batches(MaxBulkDelete, channel.getIterableHistory()
                                    .stream()
                                    //.peek(x -> log.info("filter(id): " + x))
                                    .filter(m -> m.getIdLong() != it)
                                    //.peek(x -> log.info("map(id): " + x))
                                    .map(ISnowflake::getId))
                            //.peek(ids -> log.fine(Polyfill.batches(8, ids.stream())
                            //        .map(ls -> String.join(", ", ls))
                             //       .collect(Collectors.joining("\n\t\t", "Deleting message batch:\n\t\t", ""))))
                            //.peek(x -> log.info("bulkDelete(): " + x))
                            .map(channel::deleteMessagesByIds)
                            //.peek(x -> log.info("submit(): " + x))
                            .map(RestAction::submit)
                            //.peek(x -> log.info("collect(): " + x))
                            .collect(Collectors.collectingAndThen(
                                    Collectors.<CompletableFuture<?>>toList(),
                                    all -> CompletableFuture.allOf(all.toArray(CompletableFuture[]::new)))
                            )).exceptionally(Polyfill.exceptionLogger());
            }

            @Override
            public void accept(final String txt) {
                if (txt.isBlank()) return; //todo: this shouldn't be necessary
                log.finest("accept('" + txt + "')");
                Ratelimit.run(txt, Duration.ofSeconds(fancy ? 3 : 1), msg, (msg, queue) -> {
                    var raw = MarkdownSanitizer.sanitize(msg.getContentRaw());
                    log.finest("length of raw = " + raw.length());
                    var add = oneString(raw.length(), queue);
                    RestAction<Message> chain;
                    if (fancy) {
                        var content = raw + add.getFirst();
                        chain = msg.editMessage(wrapContent(content));
                    } else chain = newMsg(add.getFirst());
                    if (!fancy && !add.getSecond())
                        chain = chain.flatMap($ -> newMsg(oneString(0, queue).getFirst()));
                    return chain.submit();
                }).exceptionally(Polyfill.exceptionLogger(log));
            }

            private Pair<@NotNull String, @NotNull Boolean> oneString(int rawLen, Queue<@NotNull String> queue) {
                boolean hasSpace = false;
                String add = "";
                while (!queue.isEmpty() && (fancy || (hasSpace = (rawLen + add + queue.peek()).length() < MaxLength))) {
                    var poll = queue.poll();
                    add += poll;
                }
                return new Pair<>(add, hasSpace);
            }

            private MessageCreateAction newMsg(@Nullable String content) {
                log.finest("new message with start content:\n" + content);
                var wrap = wrapContent(content);
                if (wrap.length() <= 6) // empty codeblock
                    throw new RuntimeException("Invalid console channel output");
                return channel.sendMessage(wrap);
            }

            private String wrapContent(@Nullable String content) {
                return MarkdownUtil.codeblock(Optional.ofNullable(content)
                        .map(this::scroll)
                        .orElseGet(this::header));
            }

            private String scroll(String content) {
                if (!fancy) return content;
                var lines = content.split("\r?\n");
                var rev = new ArrayList<String>();
                var index = lines.length - 1;
                int size = 0;
                while (index >= 0 && (size = rev.stream().mapToInt(s -> s.length() + 1).sum() + lines[index].length()) < MaxLength)
                    rev.add(lines[index--]);
                log.finest("size after scroll = " + (index < 0 ? size : (size - lines[index].length())));
                Collections.reverse(rev);
                return String.join("\n", rev) + '\n';
            }

            private String header() {
                return "New output session started at " + Date.from(Instant.now()) + '\n';
            }
        }));
    }

    public void uploadFile(long id, InputStream source, String name, String message) {
        Objects.requireNonNull(jda.getTextChannelById(id))
                .sendFiles(FileUpload.fromData(source, name))
                .setContent(message)
                .queue();
    }

    @Override
    public void closeSelf() {
        jda.shutdown();
    }
}
