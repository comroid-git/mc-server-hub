package org.comroid.mcsd.core.module.shell;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import lombok.extern.java.Log;
import org.comroid.api.Component;
import org.comroid.api.DelegateStream;
import org.comroid.api.io.FileHandle;
import org.comroid.mcsd.api.model.Status;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.module.ServerModule;
import org.comroid.mcsd.core.module.status.StatusModule;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.StringReader;
import java.net.URL;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import static java.time.Instant.now;

@Log
@Getter
@ToString
@FieldDefaults(level = AccessLevel.PRIVATE)
@Component.Requires(FileModule.class)
public class UpdateModule extends ServerModule {
    public static final Factory<UpdateModule> Factory = new Factory<>(UpdateModule.class) {
        @Override
        public UpdateModule create(Server server) {
            return new UpdateModule(server);
        }
    };

    final AtomicBoolean updateRunning = new AtomicBoolean(false);
    FileModule files;

    private UpdateModule(Server server) {
        super(server);
    }

    @Override
    protected void $initialize() {
        files=server.component(FileModule.class).assertion();
    }

    @Override
    protected void $tick() {
        super.$tick();
        if ((server.getBackupPeriod() == null || server.getLastBackup().plus(server.getBackupPeriod()).isAfter(now()))
                || updateRunning.get())
            return;
        //todo runUpdate(false);
    }

    public CompletableFuture<Boolean> runUpdate(boolean force) {
        if (!updateRunning.compareAndSet(false, true))
            return CompletableFuture.failedFuture(new RuntimeException("There is already an update running"));
        var status = server.component(StatusModule.class).assertion();
        status.pushStatus(Status.updating);

        return CompletableFuture.supplyAsync(() -> {
            try {
                // modify server.properties
                Properties prop;
                var serverProperties = new FileHandle(server.path("server.properties").toFile());
                if (!serverProperties.exists()) {
                    serverProperties.mkdirs();
                    serverProperties.createNewFile();
                }
                try (var in = new FileInputStream(serverProperties)) {
                    prop = server.updateProperties(in);
                }
                try (var out = new FileOutputStream(serverProperties, false)) {
                    prop.store(out, "Managed Server Properties by MCSD");
                }

                // download server.jar
                var serverJar = new FileHandle(server.path("server.jar").toFile());
                if (!serverJar.exists()) {
                    serverJar.mkdirs();
                    serverJar.createNewFile();
                } else if (!force && files.isJarUpToDate())
                    return false;
                try (var in = new URL(server.getJarUrl()).openStream();
                     var out = new FileOutputStream(serverJar, false)) {
                    in.transferTo(out);
                }

                // eula.txt
                var eulaTxt = new FileHandle(server.path("eula.txt").toFile());
                if (!eulaTxt.exists()) {
                    eulaTxt.mkdirs();
                    eulaTxt.createNewFile();
                }
                try (var in = new DelegateStream.Input(new StringReader("eula=true\n"));
                     var out = new FileOutputStream(eulaTxt, false)) {
                    in.transferTo(out);
                }

                status.pushStatus(Status.online.new Message("Update done"));
                return true;
            } catch (Throwable t) {
                log.log(Level.SEVERE, "An error occurred while updating", t);
                status.pushStatus(Status.in_Trouble.new Message("Update failed"));
                return false;
            } finally {
                updateRunning.set(false);
            }
        });
    }
}
