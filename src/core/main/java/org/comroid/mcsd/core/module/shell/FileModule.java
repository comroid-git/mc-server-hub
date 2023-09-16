package org.comroid.mcsd.core.module.shell;

import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.extern.java.Log;
import org.comroid.api.io.FileHandle;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.module.ServerModule;
import org.comroid.util.JSON;
import org.comroid.util.MD5;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Objects;
import java.util.Properties;

@Log
@Getter
@ToString
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FileModule extends ServerModule {
    public static final Factory<FileModule> Factory = new Factory<>(FileModule.class) {
        @Override
        public FileModule create(Server server) {
            return new FileModule(server);
        }
    };
    protected FileModule(Server server) {
        super(server);
    }

    @Override
    @SneakyThrows
    protected void $initialize() {
        updateProperties();
    }

    @SneakyThrows
    public boolean isJarUpToDate() {
        if (server.isForceCustomJar())
            return true;
        var serverJar = new FileHandle(server.path("server.jar").toFile());
        if (!serverJar.exists())
            return false;
        try (var source = new JSON.Deserializer(new URL(server.getJarInfoUrl()).openStream());
             var local = new FileInputStream(serverJar)) {
            var sourceMd5 = source.readObject().get("response").get("md5").asString("");
            var localMd5 = MD5.calculate(local);
            return sourceMd5.equals(localMd5);
        }
    }

    public Properties updateProperties() throws IOException {
        var serverProperties = new FileHandle(server.path("server.properties").toFile());
        if (!serverProperties.exists()) {
            serverProperties.mkdirs();
            serverProperties.createNewFile();
        }
        var prop = new Properties();
        try (var input = new FileInputStream(serverProperties.getAbsolutePath())) {
            prop.load(input);
        }

        prop.setProperty("server-port", String.valueOf(server.getPort()));
        prop.setProperty("max-players", String.valueOf(server.getMaxPlayers()));
        prop.setProperty("white-list", String.valueOf(server.isWhitelist() || server.isMaintenance()));

        // query
        prop.setProperty("enable-query", String.valueOf(true));
        prop.setProperty("query.port", String.valueOf(server.getQueryPort()));

        // rcon
        prop.setProperty("enable-rcon", String.valueOf(server.getRConPassword() != null && !server.getRConPassword().isBlank()));
        prop.setProperty("rcon.port", String.valueOf(server.getRConPort()));
        prop.setProperty("rcon.password", Objects.requireNonNullElse(server.getRConPassword(), ""));

        try (var out = new FileOutputStream(serverProperties, false)) {
            prop.store(out, "Managed Server Properties by MCSD");
        }
        return prop;
    }
}
