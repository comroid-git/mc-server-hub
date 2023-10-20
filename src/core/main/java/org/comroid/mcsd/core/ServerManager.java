package org.comroid.mcsd.core;

import lombok.Value;
import lombok.extern.java.Log;
import org.comroid.api.Component;
import org.comroid.api.SupplierX;
import org.comroid.api.ThrowingConsumer;
import org.comroid.api.ThrowingFunction;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.module.ServerModule;
import org.comroid.mcsd.core.repo.ServerRepo;
import org.comroid.util.Streams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

@Log
@Service
public class ServerManager {
    private final Map<UUID, Entry> cache = new ConcurrentHashMap<>();
    private @Autowired ServerRepo servers;
    private @Autowired List<ServerModule.Factory<?>> serverModuleFactories;

    public void startAll(List<Server> servers) {
        servers.stream()
                .map(ThrowingFunction.logging(log, srv -> get(srv.getId()).assertion("Could not initialize " + srv)))
                .flatMap(Streams.yield(Objects::nonNull, $ -> log.severe("A server was not initialized correctly")))
                .map(Entry::getTree)
                .forEach(tree -> tree.execute(Executors.newScheduledThreadPool(4), Duration.ofSeconds(30)));
    }

    public SupplierX<Entry> get(UUID id) {
        if (cache.containsKey(id))
            return () -> cache.get(id);
        var result = servers.findById(id);
        if (result.isEmpty())
            return SupplierX.empty();
        final var server = result.get();
        return SupplierX.of(cache.computeIfAbsent(id, k -> {
            final var modules = serverModuleFactories.stream()
                    .map(factory -> factory.create(server))
                    .toArray();
            return new Entry(server, new Component.Base(server.getBestName(), modules));
        }));
    }

    public Component tree(Server server) {
        return get(server.getId()).assertion(server + " not initialized").tree;
    }

    @Value
    public static class Entry {
        Server server;
        Component tree;
    }
}
