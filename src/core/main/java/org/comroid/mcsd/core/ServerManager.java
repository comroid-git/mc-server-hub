package org.comroid.mcsd.core;

import lombok.Value;
import lombok.extern.java.Log;
import org.comroid.api.*;
import org.comroid.mcsd.core.entity.module.ModulePrototype;
import org.comroid.mcsd.core.entity.server.Server;
import org.comroid.mcsd.core.module.ServerModule;
import org.comroid.mcsd.core.repo.module.ModuleRepo;
import org.comroid.mcsd.core.repo.server.ServerRepo;
import org.comroid.util.Streams;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static java.util.function.Predicate.not;

@Log
@Service
public class ServerManager {
    public static final Duration TickRate = Duration.ofSeconds(30);
    private final Map<UUID, Entry> cache = new ConcurrentHashMap<>();
    private @Autowired ServerRepo servers;
    private @Autowired ModuleRepo<ModulePrototype> moduleRepo;

    public void startAll(List<Server> servers) {
        servers.stream()
                .map(ThrowingFunction.logging(log, srv -> get(srv.getId()).assertion("Could not initialize " + srv)))
                .flatMap(Streams.yield(Objects::nonNull, $ -> log.severe("A server was not initialized correctly")))
                .map(entry -> {
                    var c = entry.reloadModules();
                    return "%s loaded %d modules".formatted(entry.server, c);
                })
                .forEach(log::info);
    }

    public SupplierX<Entry> get(final UUID id) {
        if (cache.containsKey(id))
            return () -> cache.get(id);
        var result = servers.findById(id);
        if (result.isEmpty())
            return SupplierX.empty();
        final var server = result.get();
        return SupplierX.of(cache.computeIfAbsent(id, k -> new Entry(server)));
    }

    public Component tree(Server server) {
        return get(server.getId()).assertion(server + " not initialized");
    }

    @Value
    public class Entry extends Component.Base {
        Server server;
        Map<ModulePrototype, ServerModule<ModulePrototype>> tree = new ConcurrentHashMap<>();
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(4, new ThreadFactory() {
            public final AtomicInteger count = new AtomicInteger(0);
            @Override
            public Thread newThread(@NotNull Runnable r) {
                return new Thread(r, "server_" + server.getId() + "_exec_"+count.incrementAndGet());
            }
        });
        AtomicReference<UncheckedCloseable> running = new AtomicReference<>();

        @Override
        public Object addChildren(Object @NotNull ... children) {
            for (Object child : children) {
                if (child instanceof ServerModule<?>) {
                    var key = ((ServerModule<?>) child).getProto();
                    tree.put(key, Polyfill.uncheckedCast(child));
                }else super.addChildren(child);
            }
            return this;
        }

        @Override
        public int removeChildren(Object @NotNull ... children) {
            var c = 0;
            for (Object child : children) {
                ModulePrototype key = null;
                if (child instanceof ModulePrototype)
                    key = ((ModulePrototype) child);
                else if (child instanceof ServerModule<?>)
                    key = ((ServerModule<?>) child).getProto();
                if (key != null) {
                    var old = tree.remove(key);
                    old.terminate();
                    c++;
                } else c += super.removeChildren(child);
            }
            return c;
        }

        @Override
        public void clearChildren() {
            super.clearChildren();
            tree.clear();
        }

        @Override
        public Stream<Object> streamOwnChildren() {
            return tree.values().stream().map(Polyfill::uncheckedCast);
        }

        /**
         * Terminates and removes all modules that are no longer in DB
         */
        public long cleanupModules() {
            var existing = Streams.of(moduleRepo.findAllByServerId(server.getId())).toList();
            var missing = tree.keySet().stream()
                    .filter(not(existing::contains))
                    .toList();
            return missing.stream()
                    .filter(proto->removeChildren(proto)>1)
                    .count();
        }

        /**
         * Loads all modules that are in DB but are not loaded as a module
         */
        public long refreshModules() {
            return Streams.of(moduleRepo.findAllByServerId(server.getId()))
                    .filter(not(tree::containsKey))
                    .<ServerModule<?>>map(proto -> {
                        log.fine("Loading proto " + proto);
                        var module = proto.toModule(server);
                        module.setParent(this);
                        addChildren(module);
                        return module;
                    })
                    .peek(this::addChildren)
                    .count();
        }

        /**
         * Terminates the entire tree and reloads all modules from scratch
         */
        public long reloadModules() {
            terminate();
            var running = this.running.get();
            if (running!=null)
                running.close();
            clearChildren();

            var count = refreshModules();
            running = execute(executor, TickRate);
            this.running.set(running);
            return count;
        }
    }
}
