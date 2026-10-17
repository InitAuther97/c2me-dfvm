package com.ishland.c2me.opts.dfc.common.ir.test.framework;

import java.util.Objects;

public final class Assert {

    private Assert() {
    }

    public static void fail(String message) {
        throw new AssertionError(message);
    }

    public static void assertTrue(boolean condition, String message) {
        if (!condition) fail(message);
    }

    public static void assertTrue(boolean condition) {
        assertTrue(condition, "Expected condition to be true");
    }

    public static void assertFalse(boolean condition, String message) {
        if (condition) fail(message);
    }

    public static void assertFalse(boolean condition) {
        assertFalse(condition, "Expected condition to be false");
    }

    public static void assertNull(Object actual, String message) {
        if (actual != null) fail(message + " (expected null, got <" + actual + ">)");
    }

    public static void assertNotNull(Object actual, String message) {
        if (actual == null) fail(message + " (expected non-null)");
    }

    public static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) {
            fail(message + " (expected same instance <" + expected + ">, got <" + actual + ">)");
        }
    }

    public static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) fail(message + " (expected <" + expected + ">, got <" + actual + ">)");
    }

    public static void assertEquals(double expected, double actual, String message) {
        if (Double.compare(expected, actual) != 0) {
            fail(message + " (expected <" + expected + ">, got <" + actual + ">)");
        }
    }

    public static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            fail(message + " (expected <" + expected + ">, got <" + actual + ">)");
        }
    }

    public static void assertEquals(Object expected, Object actual) {
        assertEquals(expected, actual, "Objects differ");
    }
}