package org.comroid.mcsd.core.module;

import lombok.SneakyThrows;
import org.comroid.api.io.FileHandle;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.util.JSON;
import org.comroid.util.MD5;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Objects;
import java.util.Properties;

public abstract class FileModule extends ServerModule {
    public FileModule(Server parent) {
        super(parent);
    }

    public abstract boolean mkDir(String path);
    public abstract boolean exists(String path);
    public abstract long size(String path);
    public abstract InputStream readFile(String path);
    public abstract OutputStream writeFile(String path);

    @SneakyThrows
    public boolean isJarUpToDate() {
        if (parent.isForceCustomJar())
            return true;
        var serverJar = new FileHandle(parent.path("server.jar").toFile());
        if (!serverJar.exists())
            return false;
        try (var source = new JSON.Deserializer(new URL(parent.getJarInfoUrl()).openStream());
             var local = readFile(serverJar.getAbsolutePath())) {
            var sourceMd5 = source.readObject().get("response").get("md5").asString("");
            var localMd5 = MD5.calculate(local);
            return sourceMd5.equals(localMd5);
        }
    }

    public Properties updateProperties() throws IOException {
        var serverProperties = parent.path("server.properties").toAbsolutePath().toString();
        mkDir(serverProperties);
        var prop = new Properties();
        try (var input = readFile(serverProperties)) {
            prop.load(input);
        }

        prop.setProperty("parent-port", String.valueOf(parent.getPort()));
        prop.setProperty("max-players", String.valueOf(parent.getMaxPlayers()));
        prop.setProperty("white-list", String.valueOf(parent.isWhitelist() || parent.isMaintenance()));

        // query
        prop.setProperty("enable-query", String.valueOf(true));
        prop.setProperty("query.port", String.valueOf(parent.getQueryPort()));

        // rcon
        prop.setProperty("enable-rcon", String.valueOf(parent.getRConPassword() != null && !parent.getRConPassword().isBlank()));
        prop.setProperty("rcon.port", String.valueOf(parent.getRConPort()));
        prop.setProperty("rcon.password", Objects.requireNonNullElse(parent.getRConPassword(), ""));

        try (var out = writeFile(serverProperties)) {
            prop.store(out, "Managed Server Properties by MCSD");
        }
        return prop;
    }
}
