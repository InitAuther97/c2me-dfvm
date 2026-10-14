package com.ishland.c2me.opts.dfc.common.ir.stamp;

public record CoordinationStamp(
        int originX, int originY, int originZ
) implements Stamp {

    /// This is not aligned for any meaningful volume
    public static final CoordinationStamp BOTTOM = new CoordinationStamp(
            8191, -64, 8191);

    /// This is aligned for any volume
    public static final CoordinationStamp TOP = new CoordinationStamp(
            0, 0, 0);
}
