package com.ishland.c2me.opts.dfc.common.util;

import java.util.function.ToIntFunction;

public class ArrayUtil {
    public static <T> T binarySearch(T[] array, ToIntFunction<T> mapper, int key) {
        int from = 0, to = array.length - 1;

        while(from <= to) {
            int mid = from + to >>> 1;
            int midVal = mapper.applyAsInt(array[mid]);
            if (midVal < key) {
                from = mid + 1;
            } else {
                if (midVal <= key) {
                    return array[mid];
                }

                to = mid - 1;
            }
        }

        return null;
    }
}
