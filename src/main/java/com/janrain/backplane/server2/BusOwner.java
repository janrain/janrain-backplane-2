package com.janrain.backplane.server2;

import com.janrain.backplane.common.User;

/**
 * @author Johnny Bufu
 */
public class BusOwner extends User {

    public BusOwner() { }

    // PACKAGE

    BusOwner(String name, String secret) {
        super(name, secret);
    }

    // PRIVATE

    private static final long serialVersionUID = -6311958895910030506L;

}
