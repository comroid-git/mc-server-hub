package org.comroid.mcsd.core.module.ssh;

import org.apache.sshd.client.SshClient;
import org.comroid.mcsd.core.entity.Server;
import org.comroid.mcsd.core.module.FileModule;
import org.comroid.mcsd.core.util.ApplicationContextProvider;

import java.io.InputStream;
import java.io.OutputStream;

public class SshFileModule extends FileModule {
    private final SshClient client;

    public SshFileModule(Server parent) {
        super(parent);
        client = ApplicationContextProvider.bean(SshClient.class)
                .;
    }

    @Override
    public boolean mkDir(String path) {
        return false;
    }

    @Override
    public boolean exists(String path) {
        return false;
    }

    @Override
    public long size(String path) {
        return 0;
    }

    @Override
    public InputStream readFile(String path) {
        return null;
    }

    @Override
    public OutputStream writeFile(String path) {
        return null;
    }
}
