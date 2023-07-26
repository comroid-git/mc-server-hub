package org.comroid.mcsd.agent.dto;

import lombok.Value;
import org.comroid.mcsd.core.entity.Agent;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.entity.User;

import java.util.List;

@Value
public class WebAppInfo {
    User user;
    Agent agent;
    List<Server> servers;
}
