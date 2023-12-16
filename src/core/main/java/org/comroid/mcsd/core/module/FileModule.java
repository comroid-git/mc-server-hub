package org.comroid.mcsd.core.module;

import lombok.SneakyThrows;
import org.comroid.api.io.FileHandle;
import org.comroid.mcsd.core.entity.server.Server;
import org.comroid.mcsd.core.entity.module.FileModulePrototype;
import org.comroid.util.JSON;
import org.comroid.util.MD5;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Objects;
import java.util.Properties;

public abstract class FileModule<T extends FileModulePrototype> extends ServerModule<T> {
    public FileModule(Server server, T proto) {
        super(server, proto);
    }

    public abstract boolean mkDir(String path);
    public abstract boolean exists(String path);
    public abstract long size(String path);
    public abstract InputStream readFile(String path);
    public abstract OutputStream writeFile(String path);

    @SneakyThrows
    public boolean isJarUpToDate() {
        if (server.isForceCustomJar())
            return true;
        var serverJar = new FileHandle(server.path("server.jar").toFile());
        if (!serverJar.exists())
            return false;
        try (var source = new JSON.Deserializer(new URL(server.getJarInfoUrl()).openStream());
             var local = readFile(serverJar.getAbsolutePath())) {
            var sourceMd5 = source.readObject().get("response").get("md5").asString("");
            var localMd5 = MD5.calculate(local);
            return sourceMd5.equals(localMd5);
        }
    }

    public Properties updateProperties() throws IOException {
        var serverProperties = server.path("server.properties").toAbsolutePath().toString();
        mkDir(serverProperties);
        var prop = new Properties();
        try (var input = readFile(serverProperties)) {
            prop.load(input);
        }

        prop.setProperty("parent-port", String.valueOf(server.getPort()));
        prop.setProperty("max-players", String.valueOf(server.getMaxPlayers()));
        prop.setProperty("white-list", String.valueOf(server.isWhitelist() || server.isMaintenance()));

        // query
        prop.setProperty("enable-query", String.valueOf(true));
        prop.setProperty("query.port", String.valueOf(server.getQueryPort()));

        // rcon
        prop.setProperty("enable-rcon", String.valueOf(server.getRConPassword() != null && !server.getRConPassword().isBlank()));
        prop.setProperty("rcon.port", String.valueOf(server.getRConPort()));
        prop.setProperty("rcon.password", Objects.requireNonNullElse(server.getRConPassword(), ""));

        try (var out = writeFile(serverProperties)) {
            prop.store(out, "Managed Server Properties by MCSD");
        }
        return prop;
    }
}
