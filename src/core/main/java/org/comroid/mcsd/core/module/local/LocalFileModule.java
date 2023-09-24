package org.comroid.mcsd.core.module.local;

import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.extern.java.Log;
import org.comroid.api.io.FileHandle;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.module.FileModule;

import java.io.*;

@Log
@Getter
@ToString
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LocalFileModule extends FileModule {
    public static final Factory<LocalFileModule> Factory = new Factory<>(LocalFileModule.class) {
        @Override
        public LocalFileModule create(Server parent) {
            return new LocalFileModule(parent);
        }
    };
    protected LocalFileModule(Server parent) {
        super(parent);
    }

    @Override
    public boolean mkDir(String path) {
        return new FileHandle(path).mkdirs();
    }

    @Override
    public boolean exists(String path) {
        return new FileHandle(path).exists();
    }

    @Override
    public long size(String path) {
        return new File(path).length();
    }

    @Override
    @SneakyThrows
    public InputStream readFile(String path) {
        return new FileInputStream(path);
    }

    @Override
    @SneakyThrows
    public OutputStream writeFile(String path) {
        if (!mkDir(path))
            throw new IOException("Failed to create directory: " + path);
        return new FileOutputStream(path, false);
    }

    @Override
    @SneakyThrows
    protected void $initialize() {
        updateProperties();
    }
}
