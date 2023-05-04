package org.comroid.util;

import lombok.SneakyThrows;
import org.comroid.api.Specifiable;
import org.comroid.api.ThrowingRunnable;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface DelegateStream extends Specifiable<DelegateStream>, AutoCloseable {
    Stream<? extends AutoCloseable> getDependencies();

    boolean addDependency(AutoCloseable dependency);

    default <T extends DelegateStream> T plus(AutoCloseable dependency) throws ClassCastException {
        if (!addDependency(dependency))
            System.err.println("Could not add dependency " + dependency + " to " + this);
        //noinspection unchecked
        return (T)this;
    }

    default Input input() {
        return (Input)this;
    }

    default Output output() {
        return (Output)this;
    }

    @Override
    @SneakyThrows
    @SuppressWarnings("RedundantThrows")
    default void close() throws Exception {
        getDependencies().filter(Objects::nonNull).forEachOrdered(AutoCloseable::close);
    }

    private static <T extends AutoCloseable> Stack<T> prepend(T it, T[] array) {
        return Stream.concat(Stream.of(it), Stream.of(array)).collect(Stack::new, Collection::add, Collection::addAll);
    }

    final class Input extends InputStream implements DelegateStream {
        private final ThrowingIntSupplier<IOException> read;
        private final Stack<Closeable> dependencies;

        public Input(InputStream delegate, Closeable... dependencies) {
            this.read = delegate::read;
            this.dependencies = prepend(delegate, dependencies);
        }

        public Input(Reader delegate, Closeable... dependencies) {
            this.read = delegate::read;
            this.dependencies = prepend(delegate, dependencies);
        }

        @Override
        public int read() throws IOException {
            return read.getAsInt();
        }

        @Override
        public Stream<? extends AutoCloseable> getDependencies() {
            return dependencies.stream();
        }

        @Override
        public boolean addDependency(AutoCloseable dependency) {
            return dependency instanceof Closeable && dependencies.add((Closeable)dependency);
        }
    }

    final class Output extends OutputStream implements DelegateStream {
        private final ThrowingIntConsumer<IOException> write;
        private final ThrowingRunnable<IOException> flush;
        private final Stack<Closeable> dependencies;

        public Output(OutputStream delegate, Closeable... dependencies) {
            this.write = delegate::write;
            this.flush = delegate::flush;
            this.dependencies = prepend(delegate, dependencies);
        }

        public Output(Writer delegate, Closeable... dependencies) {
            this.write = delegate::write;
            this.flush = delegate::flush;
            this.dependencies = prepend(delegate, dependencies);
        }

        @Override
        public void write(int b) throws IOException {
            write.accept(b);
        }

        @Override
        public void flush() throws IOException {
            flush.run();
        }

        @Override
        public Stream<? extends AutoCloseable> getDependencies() {
            return dependencies.stream();
        }

        @Override
        public boolean addDependency(AutoCloseable dependency) {
            return dependency instanceof Closeable && dependencies.add((Closeable)dependency);
        }
    }
}
