/*
 * Copyright 2012 Janrain, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.janrain.backplane.common;

import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * hash = base64(mac_key) + "." + hmac_siged(password)
 *
 * @author Johnny Bufu
 */
public class HmacHashUtils {

    // - PUBLIC

    /**
     * @return HMAC hash, or null if the supplied password is null or the hash could not be computed
     */
    public static String hmacHash(String password) {
        try {
            if (password == null) return null;
            SecretKey key = generateMacKey(HMAC_SHA256_ALGORITHM, HMAC_SHA256_LENGTH);
            byte[] encoded = key.getEncoded();
            if (encoded == null) {
                logger.error("Mac key encoding is null, cannot hash password.");
                return null;
            }
            return new String(Base64.encodeBase64(encoded), UTF8_STRING_ENCODING) + "." + hmacSign(key, password);
        } catch (Exception e) {
            logger.error("Error computing HMAC hash: " + e.getMessage());
            return null;
        }
    }

    /**
     * @return true if the provided hmacHash matches the password, false otherwise
     */
    public static boolean checkHmacHash(String password, String hmacHash) {
        if (password == null || hmacHash == null) return false;

        String[] keyAndSigned = hmacHash.split("\\.");
        if (keyAndSigned.length != 2) return false;

        try {
            byte[] encodedKey = Base64.decodeBase64(keyAndSigned[0].getBytes(UTF8_STRING_ENCODING));
            String newSigned = hmacSign(new SecretKeySpec(encodedKey, HMAC_SHA256_ALGORITHM), password);
            String pwdHash = keyAndSigned[1];

            // equal-time compare
            if (newSigned.length() == 0 || newSigned.length() != pwdHash.length()) return false;
            int result = 0;
            for (int i = 0; i < newSigned.length(); i++) {
                result |= newSigned.charAt(i) ^ pwdHash.charAt(i);
            }
            return result == 0;
        } catch (Exception e) {
            logger.error("Error checking HMAC hash: " + e.getMessage());
            return false;
        }
    }

    public static void main(String[] args) throws IOException {
        String password;
        if (args.length == 1) {
            password = args[0];
        } else {
            System.out.print("Password: ");
            password = new BufferedReader(new InputStreamReader(System.in)).readLine();
        }
        System.out.println("hmac hash: " + hmacHash(password));
    }

    // - PRIVATE

    private static final Logger logger = Logger.getLogger(HmacHashUtils.class);

    private static final String UTF8_STRING_ENCODING = "utf-8";
    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";
    private static final int HMAC_SHA256_LENGTH = 256;

    private static SecretKey generateMacKey(String algorithm, int keySize) throws NoSuchAlgorithmException {
        KeyGenerator keyGen = KeyGenerator.getInstance(algorithm);
        keyGen.init(keySize);
        return keyGen.generateKey();
    }

    private static String hmacSign(SecretKey key, String password) throws NoSuchAlgorithmException, InvalidKeyException, UnsupportedEncodingException {
        Mac mac = Mac.getInstance(key.getAlgorithm());
        mac.init(key);
        return new String(Base64.encodeBase64(mac.doFinal(password.getBytes())), UTF8_STRING_ENCODING);
    }
}
