package org.comroid.mcsd.api.model;

import org.comroid.api.attr.BitmaskAttribute;
import org.jetbrains.annotations.Nullable;

public interface IStatusMessage {
    Status getStatus();
    Scope getScope();

    @Nullable
    default String getMessage() {
        return null;
    }

    default String toStatusMessage() {
        return getStatus().getEmoji() + '\t' + "Server is " + getStatus().getName();
    }

    enum Scope implements BitmaskAttribute<Scope> {
        Public, Moderation
    }
}
