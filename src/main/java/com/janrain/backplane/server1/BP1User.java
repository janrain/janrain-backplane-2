package com.janrain.backplane.server1;

import com.janrain.backplane.common.User;

/**
 * @author Johnny Bufu
 */
public class BP1User extends User {

    public BP1User() { }

    // PACKAGE

    BP1User(String name, String secret) {
        super(name, secret);
    }

    // PRIVATE

    private static final long serialVersionUID = -8422772803895985304L;

}
