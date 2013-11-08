package com.janrain.util;

import org.junit.Test;
import scala.Option;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Johnny Bufu
 */
public class UtilsTest {

    @Test
    public void testScalaOptionGetOrNull() {
        // this should be in a java test, for as long as we need this utility method
        try {
            Option<String> opt = Option.empty();
            String value = Utils.getOrNull(opt);
            assertTrue(value == null);
        } catch (Throwable t) {
            fail("Utils.getOrNull(scalaOption) should not throw");
        }
    }
}
