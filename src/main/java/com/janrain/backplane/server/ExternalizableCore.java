package com.janrain.backplane.server;

import com.janrain.commons.message.AbstractMessage;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author Tom Raney
 */
public abstract class ExternalizableCore extends AbstractMessage implements Externalizable {

    @Override
    public void writeExternal(ObjectOutput objectOutput) throws IOException {
        HashMap<String, String> map = new HashMap<String, String>();
        Set<String> keys = this.keySet();
        for (String key : keys) {
            map.put(key, this.get(key));
        }

        objectOutput.writeObject(map);
    }

    @Override
    public void readExternal(ObjectInput objectInput) throws IOException, ClassNotFoundException {
        this.putAll((Map<? extends String, ? extends String>) objectInput.readObject());
    }
}
