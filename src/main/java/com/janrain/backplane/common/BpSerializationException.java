package com.janrain.backplane.common;

/**
 * @author Johnny Bufu
 */
public class BpSerializationException extends RuntimeException {

    public BpSerializationException(String message) {
        super(message);
    }

    public BpSerializationException(String message, Throwable cause) {
        super(message, cause);
    }

    public BpSerializationException(Throwable cause) {
        super(cause);
    }
}
