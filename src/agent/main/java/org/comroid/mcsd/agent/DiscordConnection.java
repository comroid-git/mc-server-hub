package org.comroid.mcsd.agent;

import lombok.Data;
import org.comroid.mcsd.core.entity.DiscordBotInfo;
import org.comroid.mcsd.core.entity.MinecraftProfile;
import org.comroid.mcsd.core.repo.MinecraftProfileRepo;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.channel.ServerTextChannel;
import org.javacord.api.entity.message.MessageDecoration;
import org.javacord.api.entity.message.MessageFlag;
import org.javacord.api.entity.message.embed.EmbedBuilder;
import org.javacord.api.entity.permission.PermissionType;
import org.javacord.api.entity.server.Server;
import org.javacord.api.event.interaction.SlashCommandCreateEvent;
import org.javacord.api.interaction.SlashCommand;
import org.javacord.api.interaction.SlashCommandInteractionOption;
import org.javacord.api.interaction.SlashCommandOption;
import org.javacord.api.listener.interaction.SlashCommandCreateListener;
import org.javacord.api.util.logging.ExceptionLogger;

import java.util.*;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Data
public class DiscordConnection implements SlashCommandCreateListener {
    private final ServerProcess server;
    private final DiscordBotInfo info;
    private final DiscordApi bot;

    public DiscordConnection(ServerProcess server, DiscordBotInfo info) {
        this.server = server;
        this.info = info;
        this.bot = new DiscordApiBuilder()
                .setToken(info.getToken())
                .addListener(this)
                .login()
                .join();
        createSlashCommands();
    }

    public Server getServer() {
        return bot.getServerById(info.getServerId()).orElseThrow(() -> new NoSuchElementException(bot.getYourself().getName() + " cannot find Server with ID " + info.getServerId()));
    }

    public Optional<ServerTextChannel> getPublicChannel() {
        return Optional.ofNullable(info.getPublicChannelId()).flatMap(bot::getServerTextChannelById);
    }

    public Optional<ServerTextChannel> getModerationChannel() {
        return Optional.ofNullable(info.getModerationChannelId()).flatMap(bot::getServerTextChannelById);
    }

    public Optional<ServerTextChannel> getConsoleChannel() {
        return Optional.ofNullable(info.getConsoleChannelId()).flatMap(bot::getServerTextChannelById);
    }

    private void createSlashCommands() {
        // stats
        SlashCommand.withRequiredPermissions("stats", "Shows statistics about the server")
                .createForServer(bot, info.getServerId()).join();

        // execute
        SlashCommand.withRequiredPermissions("execute", "Run a command from the console", List.of(
                SlashCommandOption.createStringOption("command", "The command to execute", true)
        ), PermissionType.ADMINISTRATOR).createForServer(bot, info.getServerId()).join();

        // account (register, view)
        SlashCommand.with("account", "Manage your Minecraft Link", List.of(
                SlashCommandOption.createSubcommand("register", "Register your Minecraft Account", List.of(
                        SlashCommandOption.createStringOption("username", "Your Minecraft Username", true)
                )),
                SlashCommandOption.createSubcommand("view", "View information about your Minecraft Account")
        )).createForServer(bot, info.getServerId()).join();
    }

    @Override
    public void onSlashCommandCreate(SlashCommandCreateEvent event) {
        final var interaction = event.getSlashCommandInteraction();
        interaction.respondLater().thenCompose(resp -> {
            switch (interaction.getCommandName()) {
                case "stats":
                    return server.getServer().status().thenCompose(stat -> resp.addEmbed(new EmbedBuilder()
                                    .setTitle("Status of %s".formatted(server.getServer()))
                                    .setDescription("%d / %d players are online%s".formatted(stat.playerCount, stat.playerMax,
                                            Optional.ofNullable(stat.players)
                                                    .map(players -> ":\n-\t" + String.join("\n-\t", players))
                                                    .orElse("")
                                    ))
                                    .setThumbnail(server.getServer().getThumbnailURL())
                                    .setFooter(stat.motd)
                                    .setAuthor(switch (stat.status) {
                                        case Offline -> "❌";
                                        case Maintenance -> "�";
                                        case Online -> "✅";
                                        default -> "⁉";
                                    } + stat.status.getName())
                                    .setUrl(server.getServer().getDashboardURL()))
                            .update());
                case "execute":
                    final var command = interaction.getOptionByName("command")
                            .flatMap(SlashCommandInteractionOption::getStringValue)
                            .orElseThrow();
                    server.getIn().println(command);
                    return resp.addEmbed(new EmbedBuilder()
                                    //.setDescription(MessageDecoration.CODE_LONG.applyToText(response)))
                                    .setDescription("Command sent"))
                            .setFlags(MessageFlag.EPHEMERAL)
                            .update();
                case "account":
                    final var subcommand = interaction.getOptionByIndex(0).orElseThrow();
                    final var profileRepo = bean(MinecraftProfileRepo.class);
                    final var userId = interaction.getUser().getId();
                    MinecraftProfile profile = null;
                    switch (subcommand.getName()) {
                        case "register":
                            var username = subcommand.getOptionByName("username")
                                    .flatMap(SlashCommandInteractionOption::getStringValue)
                                    .orElseThrow();
                            profile = profileRepo.get(username);
                            profile.setDiscordId(userId);
                            profileRepo.save(profile);
                            //no break
                        case "view":
                            if (profile == null)
                                profile = profileRepo.findByDiscordId(userId).orElseThrow();
                            break;
                    }
                    assert profile != null;
                    return resp.addEmbed(new EmbedBuilder()
                            .setThumbnail(profile.getIsoBodyURL())
                            .setAuthor(profile.getName(), profile.getNameMcURL(), profile.getHeadURL())
                            .setDescription("Has %d logins".formatted(profile.getServerLogins().size()))
                    ).update();
            }
            throw new AssertionError("Unrecognized command " + interaction.getFullCommandName());
        }).exceptionally(ExceptionLogger.get());
    }
}
