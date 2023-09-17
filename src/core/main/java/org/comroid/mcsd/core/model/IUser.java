package org.comroid.mcsd.core.model;

import org.comroid.api.Named;

import java.util.UUID;

@Deprecated
public interface IUser extends Named {
    UUID getUserId();
}
