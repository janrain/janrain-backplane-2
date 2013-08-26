package com.janrain.backplane.common;

import com.janrain.backplane2.server.Token;
import com.janrain.commons.util.EncryptUtil;
import com.janrain.commons.util.UtilsException;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;

/**
 * @author Tom Raney, Johnny Bufu
 */
public class TokenEncryptionUtils {

    // - PUBLIC

    /**
     * Parse a encrypted String representation of a Token (the cookie value)
     * that was created via the toEncryptedString() method.
     *
     * @param cookieValue		The encrypted String to parse
     * @return					A SessionCookieData object parsed from the String
     */
    public static Token fromEncryptedString(String cookieValue, String encryptionKey) throws UtilsException {

        logger.debug("Parsing encrypted session data:\n" + cookieValue);
        String unencryptedValue = EncryptUtil.decrypt(cookieValue, encryptionKey);
        ObjectMapper mapper = new ObjectMapper();
        Token data;
        try {
            data = mapper.readValue(unencryptedValue, new TypeReference<Token>() { });
        } catch (Exception e) {
            logger.error("Error reading token data from JSON '" + unencryptedValue + "'", e);
            throw new UtilsException("Unable to parse token data from cookie value", e);
        }
        return data;
    }

    /**
     * Get the String representation of a Token (for use in storing as a cookie value).
     * This can be converted back via the .fromEncryptedString() method.
     *
     * @return A encrypted String representation of the Token
     */
    public String toEncryptedString(Token token, String encryptionKey) {

        ObjectMapper mapper = new ObjectMapper();
        String value;
        try {
            value = mapper.writeValueAsString(token);
        }
        catch (Exception e) {
            logger.error("Unable to serialize Token object to JSON", e);
            value = "";
        }

        try {
            String encrypted = EncryptUtil.encrypt(value, encryptionKey);
            logger.debug("Sending encrypted Token:\n" + encrypted);
            return encrypted;
        } catch (UtilsException e) {
            logger.error("Error encrypting token: " + e.getMessage(), e);
            return "";
        }
    }

    // - PRIVATE

    private static final Logger logger = Logger.getLogger(TokenEncryptionUtils.class);

    private TokenEncryptionUtils() { }
}
