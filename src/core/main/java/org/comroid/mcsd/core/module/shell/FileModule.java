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
        public FileModule create(Server parent) {
            return new FileModule(parent);
        }
    };
    protected FileModule(Server parent) {
        super(parent);
    }

    @Override
    @SneakyThrows
    protected void $initialize() {
        updateProperties();
    }

    @SneakyThrows
    public boolean isJarUpToDate() {
        if (parent.isForceCustomJar())
            return true;
        var parentJar = new FileHandle(parent.path("parent.jar").toFile());
        if (!parentJar.exists())
            return false;
        try (var source = new JSON.Deserializer(new URL(parent.getJarInfoUrl()).openStream());
             var local = new FileInputStream(parentJar)) {
            var sourceMd5 = source.readObject().get("response").get("md5").asString("");
            var localMd5 = MD5.calculate(local);
            return sourceMd5.equals(localMd5);
        }
    }

    public Properties updateProperties() throws IOException {
        var parentProperties = new FileHandle(parent.path("parent.properties").toFile());
        if (!parentProperties.exists()) {
            parentProperties.mkdirs();
            parentProperties.createNewFile();
        }
        var prop = new Properties();
        try (var input = new FileInputStream(parentProperties.getAbsolutePath())) {
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

        try (var out = new FileOutputStream(parentProperties, false)) {
            prop.store(out, "Managed Server Properties by MCSD");
        }
        return prop;
    }
}
