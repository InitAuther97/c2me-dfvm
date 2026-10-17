package com.ishland.c2me.opts.dfc.common.ir.type;

import com.ishland.flowsched.util.Assertions;

public abstract class IrType {

    private final byte type;

    protected IrType(byte type) {
        this.type = type;
    }

    /// Compute if this type is a subtype of the given type
    public final boolean is(IrType other) {
        return this == FormalType.BOT || other == FormalType.TOP ||
                this.type == other.type ||
                other.isSubType(this);
    }

    /// Compute the OR of two types
    public final IrType meet(IrType another) {
        Assertions.assertTrue(another != null);
        if (this == another) {
            return this;
        }

        if (type == another.type) {
            return getClass().isAssignableFrom(another.getClass())
                ? another.xmeet(this) : xmeet(another);
        }

        // Handle downcast
        if (this == FormalType.TOP) {
            return another;
        } else if (another == FormalType.TOP) {
            return this;
        }

        return FormalType.BOT;
    }

    /// Compute the AND of two types in the same sub-lattice.
    public final IrType join(IrType another) {
        return dual().meet(another.dual()).dual();
    }

    public final boolean isSimilar(IrType other) {
        return this.type == other.type;
    }

    @Override
    public final boolean equals(Object another) {
        return this == another ||
                another instanceof IrType type1 &&
                        type1.type == this.type &&
                        this.isSubType(type1) &&
                        type1.isSubType(this);
    }

    @Override
    public final int hashCode() {
        return hash();
    }

    /// Compute the OR of two types in the same sub-lattice.
    protected abstract IrType xmeet(IrType another);

    /// Compute the reverse of the type in the sub-lattice.
    protected abstract IrType dual();

    /// Determine if the type information of this type is a concrete type.
    public abstract boolean isDetermined();

    /// Determine if the given type is a subtype of this type.
    protected abstract boolean isSubType(IrType another);

    /// Compute the hash code of this type.
    protected abstract int hash();

    /// Get the top type of this type, if any
    public abstract IrType top();
}
