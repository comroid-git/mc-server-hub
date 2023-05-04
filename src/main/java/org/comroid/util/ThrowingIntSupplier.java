package org.comroid.util;

public interface ThrowingIntSupplier<T extends Throwable> {
    int getAsInt() throws T;
}
