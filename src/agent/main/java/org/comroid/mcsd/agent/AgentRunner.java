package org.comroid.mcsd.agent;

import lombok.Value;
import org.comroid.api.Polyfill;
import org.comroid.mcsd.core.entity.Agent;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.repo.ServerRepo;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Value
public class AgentRunner {
    Map<UUID, ServerProcess> processes = new ConcurrentHashMap<>();
    Agent me;

    public Stream<Server> streamServers() {
        return Polyfill.stream(bean(ServerRepo.class).findAllForAgent(getMe().getId()));
    }

    public Stream<String> streamServerStatusMsgs() {
        return streamServers().map(this::process).map(ServerProcess::toString);
    }

    public ServerProcess process(final Server srv) {
        final var id = srv.getId();
        return processes.computeIfAbsent(id, $ -> new ServerProcess(srv));
    }
}
