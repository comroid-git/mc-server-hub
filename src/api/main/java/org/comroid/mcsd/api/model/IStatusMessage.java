package org.comroid.mcsd.api.model;

import org.jetbrains.annotations.Nullable;

public interface IStatusMessage {
    Status getStatus();
    @Nullable
    default String getMessage() {
        return null;
    }
}
