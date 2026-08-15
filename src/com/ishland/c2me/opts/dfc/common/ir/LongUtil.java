package com.ishland.c2me.opts.dfc.common.ir;

import it.unimi.dsi.fastutil.longs.LongArrays;
import it.unimi.dsi.fastutil.longs.LongComparator;

public class LongUtil {

    public static final LongComparator BY_LOW = LongComparator.comparingInt(LongUtil::getLow);
    public static final LongComparator BY_HIGH = LongComparator.comparingInt(LongUtil::getHigh);

    public static int getLow(long value) {
        return (int) value;
    }

    public static int getHigh(long value) {
        return (int) (value >>> 32);
    }

    public static long make(int low, int high) {
        return (long) high << 32 | Integer.toUnsignedLong(low);
    }

    public static void sortByLow(long[] arr) {
        sortByLow(arr, 0, arr.length);
    }

    public static void sortByHigh(long[] arr) {
        sortByHigh(arr, 0, arr.length);
    }

    public static void sortByLow(long[] arr, int from, int to) {
        LongArrays.quickSort(arr, from, to, BY_LOW);
    }

    public static void sortByHigh(long[] arr, int from, int to) {
        LongArrays.quickSort(arr, from, to, BY_HIGH);
    }

    public static int findByLow(long[] arr, int low) {
        return findByLow(arr, low, 0, arr.length);
    }

    public static int findByHigh(long[] arr, int high) {
        return findByHigh(arr, high, 0, arr.length);
    }

    public static int findByLow(long[] arr, int low, int from, int to) {
        return LongArrays.binarySearch(arr, from, to, make(low, 0), BY_LOW);
    }

    public static int findByHigh(long[] arr, int high, int from, int to) {
        return LongArrays.binarySearch(arr, from, to, make(0, high), BY_HIGH);
    }
}
