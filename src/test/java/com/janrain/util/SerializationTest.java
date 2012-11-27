package com.janrain.util;

import com.janrain.backplane.common.BpSerialUtils;
import com.sun.jersey.core.util.Base64;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Johnny Bufu
 */
public class SerializationTest {

    /*
    @Test
    public void serializeOld() throws Exception {

        final BusConfig2 bc2 = new BusConfig2("busz", "bozs", "68", "288999");
        final Set<Serializable> serials = new LinkedHashSet<Serializable>() {{
            add(new AuthorizationDecisionKey("bla"));
            add(new AuthorizationRequest("bla", new HashMap() {{put("client_id", new String[] {"bar"}); put("response_type", new String[]{"code"});}}));
            add(new BackplaneMessage("http://bla", 10, 100, new HashMap() {{put("channel", "barf"); put("type", "foo"); put("bus", "bzzr"); put("payload", "bbbar"); }}));
            add(new com.janrain.backplane.server.BackplaneMessage("busz", "chanell", 20, 200, new HashMap() {{put("source", "http://strm"); put("type", "barf"); put("payload", "bar"); }}));
            add(new BpServerConfig());
            add(new BusConfig1("buzs", "bosz", "100", "38765"));
            add(bc2);
            add(new Channel("idd", bc2, 40));
            add(new Client("iid", "secret", "http://source.com", "http://redirect.com"));
            add(new Grant());
            add(new Token());
            add(new User("ussr", "passwort"));
        } };

        for (Serializable serial : serials) {
            System.out.println(serial.getClass() + ":" + new String(Base64.encodeBase64(BpSerialUtils.serialize(serial))));

        }
    }
    */

    @Test
    public void deserializeOld() throws Exception {
        for (String serializedClass : serialized.keySet()) {
            Object obj = BpSerialUtils.deserialize(Base64.decode(serialized.get(serializedClass)));
            System.out.println(serializedClass + " : " + obj.getClass());
        }
    }

    private Map<String, String> serialized = new HashMap<String, String>() {{
        put("com.janrain.oauth2.AuthorizationDecisionKey", "rO0ABXNyACtjb20uamFucmFpbi5vYXV0aDIuQXV0aG9yaXphdGlvbkRlY2lzaW9uS2V50ZgJnnpC7UgMAAB4cgAvY29tLmphbnJhaW4uYmFja3BsYW5lLnNlcnZlci5FeHRlcm5hbGl6YWJsZUNvcmV3vFoR9O0j2AwAAHhwc3IAEWphdmEudXRpbC5IYXNoTWFwBQfawcMWYNEDAAJGAApsb2FkRmFjdG9ySQAJdGhyZXNob2xkeHA/QAAAAAAADHcIAAAAEAAAAAN0AAdFWFBJUkVTdAAYMjAxMi0xMS0yMFQyMzo1NzowNS45NDhadAALQVVUSF9DT09LSUV0AANibGF0AANLRVl0AB5pZnFrQjZyVzF1ZFlTaTJwY0tDazlEVWw0bURpT2J4eA==");
        put("com.janrain.oauth2.AuthorizationRequest", "rO0ABXNyACdjb20uamFucmFpbi5vYXV0aDIuQXV0aG9yaXphdGlvblJlcXVlc3RG2+YTQAvIdQwAAHhyAC9jb20uamFucmFpbi5iYWNrcGxhbmUuc2VydmVyLkV4dGVybmFsaXphYmxlQ29yZXe8WhH07SPYDAAAeHBzcgARamF2YS51dGlsLkhhc2hNYXAFB9rBwxZg0QMAAkYACmxvYWRGYWN0b3JJAAl0aHJlc2hvbGR4cD9AAAAAAAAMdwgAAAAQAAAABHQAB0VYUElSRVN0ABgyMDEyLTExLTIxVDAwOjEyOjA1Ljk2MFp0AAlDTElFTlRfSUR0AANiYXJ0AA1SRVNQT05TRV9UWVBFdAAEY29kZXQABkNPT0tJRXQAA2JsYXh4");
        put("com.janrain.backplane2.server.BackplaneMessage", "rO0ABXNyAC5jb20uamFucmFpbi5iYWNrcGxhbmUyLnNlcnZlci5CYWNrcGxhbmVNZXNzYWdlpEVKj81CaVcMAAB4cgAvY29tLmphbnJhaW4uYmFja3BsYW5lLnNlcnZlci5FeHRlcm5hbGl6YWJsZUNvcmV3vFoR9O0j2AwAAHhwc3IAEWphdmEudXRpbC5IYXNoTWFwBQfawcMWYNEDAAJGAApsb2FkRmFjdG9ySQAJdGhyZXNob2xkeHA/QAAAAAAADHcIAAAAEAAAAAh0AAJpZHQAIzIwMTItMTEtMjBUMjM6NTI6MDUuOTc3Wi1udGVNaXEzQW5CdAAGZXhwaXJldAAUMjAxMi0xMS0yMFQyMzo1MjoxNlp0AAZzdGlja3l0AAVmYWxzZXQAA2J1c3QABGJ6enJ0AAZzb3VyY2V0AApodHRwOi8vYmxhdAAHcGF5bG9hZHQAByJiYmJhciJ0AAR0eXBldAADZm9vdAAHY2hhbm5lbHQABGJhcmZ4eA==");
        put("com.janrain.backplane.server.BackplaneMessage", "rO0ABXNyAC1jb20uamFucmFpbi5iYWNrcGxhbmUuc2VydmVyLkJhY2twbGFuZU1lc3NhZ2XAJCBXH2BE3QwAAHhyAC9jb20uamFucmFpbi5iYWNrcGxhbmUuc2VydmVyLkV4dGVybmFsaXphYmxlQ29yZXe8WhH07SPYDAAAeHBzcgARamF2YS51dGlsLkhhc2hNYXAFB9rBwxZg0QMAAkYACmxvYWRGYWN0b3JJAAl0aHJlc2hvbGR4cD9AAAAAAAAMdwgAAAAQAAAACHQADGNoYW5uZWxfbmFtZXQAB2NoYW5lbGx0AAJpZHQAIzIwMTItMTEtMjBUMjM6NTI6MDYuNDYzWi1SVE53RmtuNUVndAAGZXhwaXJldAAUMjAxMi0xMS0yMFQyMzo1MjoyNlp0AAZzdGlja3l0AAVmYWxzZXQAA2J1c3QABGJ1c3p0AAZzb3VyY2V0AAtodHRwOi8vc3RybXQAB3BheWxvYWR0AAUiYmFyInQABHR5cGV0AARiYXJmeHg=");
        put("com.janrain.backplane.server.config.BpServerConfig", "rO0ABXNyADJjb20uamFucmFpbi5iYWNrcGxhbmUuc2VydmVyLmNvbmZpZy5CcFNlcnZlckNvbmZpZ+ZiqGRbymOsDAAAeHIAL2NvbS5qYW5yYWluLmJhY2twbGFuZS5zZXJ2ZXIuRXh0ZXJuYWxpemFibGVDb3Jld7xaEfTtI9gMAAB4cHNyABFqYXZhLnV0aWwuSGFzaE1hcAUH2sHDFmDRAwACRgAKbG9hZEZhY3RvckkACXRocmVzaG9sZHhwP0AAAAAAAAx3CAAAABAAAAAGdAASVE9LRU5fQ0FDSEVfTUFYX01CdAADMTAwdAAYQ09ORklHX0NBQ0hFX0FHRV9TRUNPTkRTdAACMTB0AAJJRHQADmJwc2VydmVyY29uZmlndAAKREVCVUdfTU9ERXQABWZhbHNldAAYQ0xFQU5VUF9JTlRFUlZBTF9NSU5VVEVTdAABMnQAFERFRkFVTFRfTUVTU0FHRVNfTUFYdAACNTB4eA==");
        put("com.janrain.backplane.server.BusConfig1", "rO0ABXNyACdjb20uamFucmFpbi5iYWNrcGxhbmUuc2VydmVyLkJ1c0NvbmZpZzEkj9hWWgt+VgwAAHhyAC9jb20uamFucmFpbi5iYWNrcGxhbmUuc2VydmVyLkV4dGVybmFsaXphYmxlQ29yZXe8WhH07SPYDAAAeHBzcgARamF2YS51dGlsLkhhc2hNYXAFB9rBwxZg0QMAAkYACmxvYWRGYWN0b3JJAAl0aHJlc2hvbGR4cD9AAAAAAAAMdwgAAAAQAAAABHQACEJVU19OQU1FdAAEYnV6c3QAFlJFVEVOVElPTl9USU1FX1NFQ09ORFN0AAMxMDB0AB1SRVRFTlRJT05fU1RJQ0tZX1RJTUVfU0VDT05EU3QABTM4NzY1dAAEYm9zenQABkdFVEFMTHh4");
        put("com.janrain.backplane2.server.config.BusConfig2", "rO0ABXNyAC9jb20uamFucmFpbi5iYWNrcGxhbmUyLnNlcnZlci5jb25maWcuQnVzQ29uZmlnMlhxc4f8T4bEDAAAeHIAL2NvbS5qYW5yYWluLmJhY2twbGFuZS5zZXJ2ZXIuRXh0ZXJuYWxpemFibGVDb3Jld7xaEfTtI9gMAAB4cHNyABFqYXZhLnV0aWwuSGFzaE1hcAUH2sHDFmDRAwACRgAKbG9hZEZhY3RvckkACXRocmVzaG9sZHhwP0AAAAAAAAx3CAAAABAAAAAEdAAIQlVTX05BTUV0AARidXN6dAAWUkVURU5USU9OX1RJTUVfU0VDT05EU3QAAjY4dAAdUkVURU5USU9OX1NUSUNLWV9USU1FX1NFQ09ORFN0AAYyODg5OTl0AAVPV05FUnQABGJvenN4eA==");
        put("com.janrain.backplane2.server.Channel", "rO0ABXNyACVjb20uamFucmFpbi5iYWNrcGxhbmUyLnNlcnZlci5DaGFubmVsQ+Sb1671angMAAB4cgAvY29tLmphbnJhaW4uYmFja3BsYW5lLnNlcnZlci5FeHRlcm5hbGl6YWJsZUNvcmV3vFoR9O0j2AwAAHhwc3IAEWphdmEudXRpbC5IYXNoTWFwBQfawcMWYNEDAAJGAApsb2FkRmFjdG9ySQAJdGhyZXNob2xkeHA/QAAAAAAADHcIAAAAEAAAAAV0AAJpZHQAA2lkZHQADmV4cGlyZV9zZWNvbmRzdAACNDB0AANidXN0AARidXN6dAAabWVzc2FnZV9leHBpcmVfbWF4X3NlY29uZHN0AAYyODg5OTl0AB5tZXNzYWdlX2V4cGlyZV9kZWZhdWx0X3NlY29uZHN0AAI2OHh4");
        put("com.janrain.backplane2.server.config.Client", "rO0ABXNyACtjb20uamFucmFpbi5iYWNrcGxhbmUyLnNlcnZlci5jb25maWcuQ2xpZW50m9ob6ONgqeAMAAB4cgApY29tLmphbnJhaW4uYmFja3BsYW5lMi5zZXJ2ZXIuY29uZmlnLlVzZXL66M1A7uux/wwAAHhyAC9jb20uamFucmFpbi5iYWNrcGxhbmUuc2VydmVyLkV4dGVybmFsaXphYmxlQ29yZXe8WhH07SPYDAAAeHBzcgARamF2YS51dGlsLkhhc2hNYXAFB9rBwxZg0QMAAkYACmxvYWRGYWN0b3JJAAl0aHJlc2hvbGR4cD9AAAAAAAAMdwgAAAAQAAAABHQAClNPVVJDRV9VUkx0ABFodHRwOi8vc291cmNlLmNvbXQADFJFRElSRUNUX1VSSXQAE2h0dHA6Ly9yZWRpcmVjdC5jb210AAdQV0RIQVNIdAAGc2VjcmV0dAAEVVNFUnQAA2lpZHh4");
        put("com.janrain.backplane2.server.Grant", "rO0ABXNyACNjb20uamFucmFpbi5iYWNrcGxhbmUyLnNlcnZlci5HcmFudPKO/8NuEBZWDAAAeHIAL2NvbS5qYW5yYWluLmJhY2twbGFuZS5zZXJ2ZXIuRXh0ZXJuYWxpemFibGVDb3Jld7xaEfTtI9gMAAB4cHNyABFqYXZhLnV0aWwuSGFzaE1hcAUH2sHDFmDRAwACRgAKbG9hZEZhY3RvckkACXRocmVzaG9sZHhwP0AAAAAAAAx3CAAAABAAAAAAeHg=");
        put("com.janrain.backplane2.server.Token", "rO0ABXNyACNjb20uamFucmFpbi5iYWNrcGxhbmUyLnNlcnZlci5Ub2tlbq5a8c3s+PKADAAAeHIAL2NvbS5qYW5yYWluLmJhY2twbGFuZS5zZXJ2ZXIuRXh0ZXJuYWxpemFibGVDb3Jld7xaEfTtI9gMAAB4cHNyABFqYXZhLnV0aWwuSGFzaE1hcAUH2sHDFmDRAwACRgAKbG9hZEZhY3RvckkACXRocmVzaG9sZHhwP0AAAAAAAAx3CAAAABAAAAAAeHg=");
        put("com.janrain.backplane2.server.config.User", "rO0ABXNyACljb20uamFucmFpbi5iYWNrcGxhbmUyLnNlcnZlci5jb25maWcuVXNlcvrozUDu67H/DAAAeHIAL2NvbS5qYW5yYWluLmJhY2twbGFuZS5zZXJ2ZXIuRXh0ZXJuYWxpemFibGVDb3Jld7xaEfTtI9gMAAB4cHNyABFqYXZhLnV0aWwuSGFzaE1hcAUH2sHDFmDRAwACRgAKbG9hZEZhY3RvckkACXRocmVzaG9sZHhwP0AAAAAAAAx3CAAAABAAAAACdAAHUFdESEFTSHQAWTV4OVY0c0NUSXhROVN5ZkMrTVNwWXBEakJRVnU4WVRBaHN3dzYzQVlFVXM9LklENjMzMWltQnpHSGU3T3d4enVGV0x2dUVuVjBZdmJjLzdkaHcyKzhxRTA9dAAEVVNFUnQADHRlc3RCdXNPd25lcnh4");
    }};
}
