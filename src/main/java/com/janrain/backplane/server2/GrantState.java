package com.janrain.backplane.server2;

/**
 * Models the states a Grant can be in.
 */
public enum GrantState {

    INACTIVE,

    ACTIVE(true),

    REVOKED;

    public boolean isActive() {
        return active;
    }

    // - PRIVATE

    private final boolean active;

    private GrantState() {
        this(false);
    }

    private GrantState(boolean active) {
        this.active = active;
    }
}
