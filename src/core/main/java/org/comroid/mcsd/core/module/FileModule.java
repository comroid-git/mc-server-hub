package org.comroid.mcsd.core.module;

import lombok.SneakyThrows;
import lombok.Value;
import org.comroid.api.io.FileHandle;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.util.JSON;
import org.comroid.util.MD5;

import java.io.FileInputStream;
import java.net.URL;

@Value
public class FileModule extends ServerModule {
    public static final Factory<FileModule> Factory = new Factory<>(FileModule.class) {
        @Override
        public FileModule create(Server server) {
            return new FileModule(server);
        }
    };

    private FileModule(Server server) {
        super(server);
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
}
