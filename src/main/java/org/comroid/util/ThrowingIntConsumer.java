package org.comroid.util;

public interface ThrowingIntConsumer<T extends Throwable> {
    void accept(int value) throws T;
}
