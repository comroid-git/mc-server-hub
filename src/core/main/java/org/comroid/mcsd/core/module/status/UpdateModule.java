package org.comroid.mcsd.core.module.status;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Synchronized;
import lombok.ToString;
import lombok.experimental.FieldDefaults;
import lombok.extern.java.Log;
import org.comroid.api.Component;
import org.comroid.api.DelegateStream;
import org.comroid.mcsd.api.model.Status;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.module.FileModule;
import org.comroid.mcsd.core.module.ServerModule;
import org.jetbrains.annotations.Nullable;

import java.io.StringReader;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import static java.time.Instant.now;

@Log
@Getter
@ToString
@FieldDefaults(level = AccessLevel.PRIVATE)
@Component.Requires({FileModule.class, StatusModule.class})
public class UpdateModule extends ServerModule {
    public static final Factory<UpdateModule> Factory = new Factory<>(UpdateModule.class) {
        @Override
        public UpdateModule create(Server parent) {
            return new UpdateModule(parent);
        }
    };

    final AtomicReference<@Nullable CompletableFuture<Boolean>> updateRunning = new AtomicReference<>();
    FileModule files;

    private UpdateModule(Server parent) {
        super(parent);
    }

    @Override
    protected void $initialize() {
        files=parent.component(FileModule.class).assertion();
    }

    @Override
    protected void $tick() {
        if ((parent.getBackupPeriod() == null || parent.getLastBackup().plus(parent.getBackupPeriod()).isAfter(now()))
                || (updateRunning.get()!=null&&!updateRunning.get().isDone()))
            return;
        //todo: handle if parent is running
        //runUpdate(false);
    }

    @Synchronized("updateRunning")
    public CompletableFuture<Boolean> runUpdate(boolean force) {
        if (updateRunning.get()!=null)
            return updateRunning.get();
        if (!force && parent.component(FileModule.class).map(FileModule::isJarUpToDate).orElse(false))
            return CompletableFuture.completedFuture(false);
        var status = parent.component(StatusModule.class).assertion();
        log.info("Updating " + parent);
        status.pushStatus(Status.updating);

        var fut = CompletableFuture.supplyAsync(() -> {
            try {
                // modify parent.properties
                files.updateProperties();
                //try (var prop = files.writeFile(serverProperties)) {properties.store(prop, "MCSD Managed Server Properties");}

                // download parent.jar
                var parentJar = parent.path("parent.jar").toAbsolutePath().toString();
                if (!force && files.isJarUpToDate())
                    return false;
                try (var in = new URL(parent.getJarUrl()).openStream();
                     var out = files.writeFile(parentJar)) {
                    in.transferTo(out);
                }

                // eula.txt
                var eulaTxt = parent.path("eula.txt").toAbsolutePath().toString();
                if (!files.exists(eulaTxt))
                    files.mkDir(eulaTxt);
                try (var in = new DelegateStream.Input(new StringReader("eula=true\n"));
                     var out = files.writeFile(eulaTxt)) {
                    in.transferTo(out);
                }

                status.pushStatus(Status.online.new Message("Update done"));
                return true;
            } catch (Throwable t) {
                log.log(Level.SEVERE, "An error occurred while updating", t);
                status.pushStatus(Status.in_Trouble.new Message("Update failed"));
                return false;
            } finally {
                updateRunning.set(null);
            }
        });
        updateRunning.set(fut);
        return fut;
    }
}
