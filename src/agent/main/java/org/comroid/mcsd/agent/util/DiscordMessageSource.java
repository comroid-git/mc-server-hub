package org.comroid.mcsd.agent.util;

import lombok.AllArgsConstructor;
import net.dv8tion.jda.api.EmbedBuilder;
import org.comroid.api.StreamSupplier;
import org.comroid.mcsd.core.entity.MinecraftProfile;
import org.comroid.util.Streams;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

@AllArgsConstructor
public final class DiscordMessageSource implements StreamSupplier<Object> {
    private @Nullable Object data;

    @Override
    public Stream<Object> stream() {
        return Optional.ofNullable(data).stream();
    }
    public Optional<String> string() {
        return stream()
                .map(String::valueOf)
                .findAny();
    }

    public Optional<EmbedBuilder> embed() {
        return stream()
                .flatMap(Streams.cast(EmbedBuilder.class))
                .findAny();
    }

    public DiscordMessageSource embed(final Consumer<EmbedBuilder> embedModifier) {
        data = embed().map(embed -> {
                    embedModifier.accept(embed);
                    return embed;
                })
                .orElse(null);
        return this;
    }

    public DiscordMessageSource player(MinecraftProfile player) {
        return embed(embed -> embed.setAuthor(player.getName(), player.getNameMcURL(), player.getHeadURL()));
    }

    public CompletableFuture<?> send(Sender sender) {
        return send(sender::sendString, sender::sendEmbed);
    }

    public CompletableFuture<?> send(
            final Function<String, CompletableFuture<?>> string,
            final Function<EmbedBuilder, CompletableFuture<?>> embed
    ) {
        return embed(e -> e.setTimestamp(Instant.now()))
                .embed()
                .map(embed)
                .or(()->string().map(string))
                .orElseThrow();
    }

    public interface Sender {
        CompletableFuture<?> sendString(String message);
        CompletableFuture<?> sendEmbed(EmbedBuilder builder);
    }
}
