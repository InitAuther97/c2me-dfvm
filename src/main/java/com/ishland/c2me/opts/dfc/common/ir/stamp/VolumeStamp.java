package com.ishland.c2me.opts.dfc.common.ir.stamp;

import com.ishland.c2me.opts.dfc.common.ir.type.hir.StampType;
import com.ishland.c2me.opts.dfc.common.ir.type.mir.IntegerType;

/// The i32 types of the nine volume arguments of a kernel, in argument
/// order: `sizeX, sizeY, sizeZ, minBlockX, minBlockY, minBlockZ,
/// stepBlockX, stepBlockY, stepBlockZ`. Each field carries the
/// {@link IntegerType} range of the corresponding argument.
public record VolumeStamp(
        IntegerType sizeX, IntegerType sizeY, IntegerType sizeZ,
        IntegerType minBlockX, IntegerType minBlockY, IntegerType minBlockZ,
        IntegerType stepBlockX, IntegerType stepBlockY, IntegerType stepBlockZ
) implements Stamp {

    /// No information about any of the nine arguments.
    public static final VolumeStamp TOP = new VolumeStamp(
            IntegerType.TOP, IntegerType.TOP, IntegerType.TOP,
            IntegerType.TOP, IntegerType.TOP, IntegerType.TOP,
            IntegerType.TOP, IntegerType.TOP, IntegerType.TOP
    );

    public static final StampType TOP_TYPE = StampType.create(TOP);

    @Override
    public Stamp merge(Stamp other) {
        final VolumeStamp that = (VolumeStamp) other;
        return new VolumeStamp(
                (IntegerType) sizeX.meet(that.sizeX),
                (IntegerType) sizeY.meet(that.sizeY),
                (IntegerType) sizeZ.meet(that.sizeZ),
                (IntegerType) minBlockX.meet(that.minBlockX),
                (IntegerType) minBlockY.meet(that.minBlockY),
                (IntegerType) minBlockZ.meet(that.minBlockZ),
                (IntegerType) stepBlockX.meet(that.stepBlockX),
                (IntegerType) stepBlockY.meet(that.stepBlockY),
                (IntegerType) stepBlockZ.meet(that.stepBlockZ)
        );
    }
}