package org.comroid.mcsd.core.module.game;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.Value;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.scp.server.ScpCommandFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.AsyncAuthException;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.password.PasswordChangeRequiredException;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.sftp.server.*;
import org.comroid.api.net.Token;
import org.comroid.mcsd.core.MCSD;
import org.comroid.mcsd.core.entity.module.game.ComputerCraftPlayerFileSystemProviderModulePrototype;
import org.comroid.mcsd.core.entity.server.Server;
import org.comroid.mcsd.core.entity.system.User;
import org.comroid.mcsd.core.exception.EntityNotFoundException;
import org.comroid.mcsd.core.module.ServerModule;
import org.comroid.mcsd.core.repo.system.UserRepo;

import java.io.IOException;
import java.nio.channels.Channel;
import java.nio.channels.FileLock;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.Principal;
import java.util.*;
import java.util.stream.Stream;

import static org.comroid.mcsd.core.util.ApplicationContextProvider.bean;

@Getter
@Setter
public abstract class ComputerCraftPlayerFileSystemProviderModule extends ServerModule<ComputerCraftPlayerFileSystemProviderModulePrototype> {
    private SshServer sshd;

    public ComputerCraftPlayerFileSystemProviderModule(Server server, ComputerCraftPlayerFileSystemProviderModulePrototype proto) {
        super(server, proto);
    }

    @Override
    public Stream<Object> streamOwnChildren() {
        return Stream.of(sshd);
    }

    @Override
    @SneakyThrows
    protected void $initialize() {
        sshd = SshServer.setUpDefaultServer();
        sshd.setPort(proto.getServerPort());

        sshd.setSubsystemFactories(List.of(new SftpSubsystemFactory.Builder().build()));
        sshd.setCommandFactory(new ScpCommandFactory.Builder().build());

        var sftpSubsystemFactory = new SftpSubsystemFactory.Builder()
                .withFileSystemAccessor(new VirtualFileSystem())
                .build();
        var userAuthFactory = new UserProvider();

        sshd.setSubsystemFactories(List.of(sftpSubsystemFactory));
        sshd.setPasswordAuthenticator(new UserProvider());
        sshd.start();
    }

    @Value
    private class UserProvider implements PasswordAuthenticator {
        @Override
        public boolean authenticate(String username, String password, ServerSession serverSession)
                throws PasswordChangeRequiredException, AsyncAuthException {
            return bean(UserRepo.class).findByName(username)
                    .map(user -> proto.getUserPasswords().getOrDefault(user, null))
                    .filter(password::equals)
                    .or(() -> {
                        var pw = Token.random(12, false);
                        bean(MCSD.class).getModules_computercraft()
                                .updateModuleState();
                        return Optional.of(pw);
                    })
        }

        @Override
        public boolean handleClientPasswordChangeRequest(ServerSession session, String username, String oldPassword, String newPassword) {
            var pw = bean(UserRepo.class).findByName(username)
                    .map(user -> proto.getUserPasswords().getOrDefault(user, null))
                    .orElseThrow(() -> new EntityNotFoundException(User.class, username));
        }
    }

    @Value
    private class VirtualFileSystem implements SftpFileSystemAccessor {
        @Override
        public Path resolveLocalFilePath(SftpSubsystemProxy subsystem, Path rootDir, String remotePath) throws IOException, InvalidPathException {
        }

        @Override
        public LinkOption[] resolveFileAccessLinkOptions(SftpSubsystemProxy subsystem, Path file, int cmd, String extension, boolean followLinks) throws IOException {
        }

        @Override
        public NavigableMap<String, Object> resolveReportedFileAttributes(SftpSubsystemProxy subsystem, Path file, int flags, NavigableMap<String, Object> attrs, LinkOption... options) throws IOException {
        }

        @Override
        public void applyExtensionFileAttributes(SftpSubsystemProxy subsystem, Path file, Map<String, byte[]> extensions, LinkOption... options) throws IOException {
        }

        @Override
        public void putRemoteFileName(SftpSubsystemProxy subsystem, Path path, Buffer buf, String name, boolean shortName) throws IOException {
        }

        @Override
        public SeekableByteChannel openFile(SftpSubsystemProxy subsystem, FileHandle fileHandle, Path file, String handle, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        }

        @Override
        public FileLock tryLock(SftpSubsystemProxy subsystem, FileHandle fileHandle, Path file, String handle, Channel channel, long position, long size, boolean shared) throws IOException {
        }

        @Override
        public void syncFileData(SftpSubsystemProxy subsystem, FileHandle fileHandle, Path file, String handle, Channel channel) throws IOException {
        }

        @Override
        public void closeFile(SftpSubsystemProxy subsystem, FileHandle fileHandle, Path file, String handle, Channel channel, Set<? extends OpenOption> options) throws IOException {
        }

        @Override
        public DirectoryStream<Path> openDirectory(SftpSubsystemProxy subsystem, DirectoryHandle dirHandle, Path dir, String handle, LinkOption... linkOptions) throws IOException {
        }

        @Override
        public void closeDirectory(SftpSubsystemProxy subsystem, DirectoryHandle dirHandle, Path dir, String handle, DirectoryStream<Path> ds) throws IOException {
        }

        @Override
        public Map<String, ?> readFileAttributes(SftpSubsystemProxy subsystem, Path file, String view, LinkOption... options) throws IOException {
        }

        @Override
        public void setFileAttribute(SftpSubsystemProxy subsystem, Path file, String view, String attribute, Object value, LinkOption... options) throws IOException {
        }

        @Override
        public UserPrincipal resolveFileOwner(SftpSubsystemProxy subsystem, Path file, UserPrincipal name) throws IOException {
        }

        @Override
        public void setFileOwner(SftpSubsystemProxy subsystem, Path file, Principal value, LinkOption... options) throws IOException {
        }

        @Override
        public GroupPrincipal resolveGroupOwner(SftpSubsystemProxy subsystem, Path file, GroupPrincipal name) throws IOException {
        }

        @Override
        public void setGroupOwner(SftpSubsystemProxy subsystem, Path file, Principal value, LinkOption... options) throws IOException {
        }

        @Override
        public void setFilePermissions(SftpSubsystemProxy subsystem, Path file, Set<PosixFilePermission> perms, LinkOption... options) throws IOException {
        }

        @Override
        public void setFileAccessControl(SftpSubsystemProxy subsystem, Path file, List<AclEntry> acl, LinkOption... options) throws IOException {
        }

        @Override
        public void createDirectory(SftpSubsystemProxy subsystem, Path path) throws IOException {
        }

        @Override
        public void createLink(SftpSubsystemProxy subsystem, Path link, Path existing, boolean symLink) throws IOException {
        }

        @Override
        public String resolveLinkTarget(SftpSubsystemProxy subsystem, Path link) throws IOException {
        }

        @Override
        public void renameFile(SftpSubsystemProxy subsystem, Path oldPath, Path newPath, Collection<CopyOption> opts) throws IOException {
        }

        @Override
        public void copyFile(SftpSubsystemProxy subsystem, Path src, Path dst, Collection<CopyOption> opts) throws IOException {
        }

        @Override
        public void removeFile(SftpSubsystemProxy subsystem, Path path, boolean isDirectory) throws IOException {
        }

        @Override
        public boolean noFollow(Collection<?> opts) {
        }
    }
}
