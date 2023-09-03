package org.comroid.mcsd.core.module;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.extern.java.Log;
import org.comroid.api.Polyfill;
import org.comroid.api.io.FileHandle;
import org.comroid.mcsd.api.model.Status;
import org.comroid.mcsd.core.entity.Backup;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.repo.BackupRepo;
import org.comroid.util.Archiver;
import org.comroid.util.PathUtil;
import org.comroid.util.Stopwatch;

import java.io.File;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.regex.Pattern;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Log
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BackupModule extends ServerModule {
    public static final Pattern SaveCompletePattern = ConsoleModule.pattern("Saved the game");
    public static final Factory<BackupModule> Factory = new Factory<>(BackupModule.class) {
        @Override
        public BackupModule create(Server server) {
            return new BackupModule(server);
        }
    };

    final AtomicReference<CompletableFuture<File>> currentBackup = new AtomicReference<>(CompletableFuture.completedFuture(null));
    ConsoleModule consoleModule;

    private BackupModule(Server server) {
        super(server);
    }

    @Override
    protected void $initialize() {
        super.$initialize();
        consoleModule = server.component(ConsoleModule.class).assertion();
    }

    public CompletableFuture<File> runBackup(final boolean important) {
        if (!currentBackup.get().isDone()) {
            log.warning("A backup on server %s is already running".formatted(server));
            return currentBackup.get();
        }
        var status = server.component(StatusModule.class).assertion();

        status.pushStatus(Status.running_backup);
        final var stopwatch = Stopwatch.start("backup-" + server.getId());

        var backupDir = new FileHandle(server.shCon().orElseThrow().getBackupsDir()).createSubDir(server.getName());
        if (!backupDir.exists() && !backupDir.mkdirs())
            return CompletableFuture.failedFuture(new RuntimeException("Could not create backup directory"));

        final var time = Instant.now();
        var backup = consoleModule.execute("save-off\r\nsave-all", SaveCompletePattern)
                // wait for save to finish
                .thenCompose($ -> Archiver.find(Archiver.ReadOnly).zip()
                        // do run backup
                        .inputDirectory(server.path().toAbsolutePath())
                        .excludePattern("**cache/**")
                        .excludePattern("**libraries/**")
                        .excludePattern("**versions/**")
                        .excludePattern("**dynmap/web/**") // do not backup dynmap
                        .excludePattern("**.lock")
                        .outputPath(Paths.get(backupDir.getAbsolutePath(), "backup-" + PathUtil.sanitize(time)).toAbsolutePath())
                        .execute()
                        //.orTimeout(30, TimeUnit.SECONDS) // dev variant
                        .orTimeout(1, TimeUnit.HOURS)
                        .whenComplete((r, t) -> {
                            var stat = Status.online;
                            var duration = stopwatch.stop();
                            var sizeKb = r != null ? (r.length() / (1024)) : 0;
                            var msg = "Backup finished; took %s; size: %1.2fGB".formatted(
                                    Polyfill.durationString(duration),
                                    (double) sizeKb / (1024 * 1024));
                            if (r != null)
                                bean(BackupRepo.class)
                                        .save(new Backup(time, server, sizeKb, duration, r.getAbsolutePath(), important));
                            if (t != null) {
                                stat = Status.in_Trouble;
                                msg = "Unable to complete Backup";
                                log.log(Level.SEVERE, msg + " for " + server, t);
                            }
                            consoleModule.execute("save-on");
                            status.pushStatus(stat.new Message(msg));
                        }));
        currentBackup.set(backup);
        return backup;
    }
}