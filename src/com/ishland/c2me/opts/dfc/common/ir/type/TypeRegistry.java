package com.ishland.c2me.opts.dfc.common.ir.type;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.objects.Reference2ByteLinkedOpenHashMap;

public class TypeRegistry {
    public static final Reference2ByteLinkedOpenHashMap<Class<? extends IrType>> TYPES = new Reference2ByteLinkedOpenHashMap<>();
    private static boolean frozen = false;

    static {
        TYPES.defaultReturnValue((byte) -1);
    }

    public static byte register(Class<? extends IrType> type) {
        synchronized (TYPES) {
            Preconditions.checkState(!frozen, "Attempt to register type after registry is frozen");
            final byte result = (byte) (TYPES.size() + 1);
            if (result < 0) {
                throw new UnsupportedOperationException("Too many node types... are you serious?");
            }
            if (-1 != TYPES.putIfAbsent(type, result)) {
                throw new IllegalStateException("Attempt to register type twice");
            }
            return result;
        }
    }

    private TypeRegistry() {
        throw new UnsupportedOperationException("What are you doing");
    }
}
