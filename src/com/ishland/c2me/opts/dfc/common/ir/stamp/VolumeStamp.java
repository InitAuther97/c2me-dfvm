package com.ishland.c2me.opts.dfc.common.ir.stamp;

import com.ishland.c2me.opts.dfc.common.ir.type.hir.StampType;
import com.ishland.flowsched.util.Assertions;

public record VolumeStamp(
        int stepX, int stepY, int stepZ,
        int sizeX, int sizeY, int sizeZ,
        CoordinationStamp origin
) implements Stamp {

    public VolumeStamp {
        Assertions.assertTrue(stepX >= 0 && stepY >= 0 && stepZ >= 0, "step must be >= 0");
        Assertions.assertTrue(sizeX >= 0 && sizeY >= 0 && sizeZ >= 0, "size must be >= 0");
    }

    public static StampType STAMP_ALL = new StampType(VolumeStamp.all());
    public static int MAXIMUM_DIM_SIZE = 1 << 16;

    static VolumeStamp TOP = new VolumeStamp(
            0, 0, 0,
            0, 0, 0,
            CoordinationStamp.TOP
    );

    static VolumeStamp BOTTOM = new VolumeStamp(
            1, 1, 1,
            Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE,
            CoordinationStamp.BOTTOM
    );

    public boolean isNormal() {
        return Integer.bitCount(stepX) == 1 && Integer.bitCount(stepY) == 1 && Integer.bitCount(stepZ) == 1;
    }

    public boolean isAligned() {
        return origin.originX() % sizeX == 0 &&
                origin().originY() % sizeY == 0 &&
                origin.originZ() % sizeZ == 0;
    }

    public int dimX() {
        return stepX * sizeX;
    }

    public int dimY() {
        return stepY * sizeY;
    }

    public int dimZ() {
        return stepZ * sizeZ;
    }

    public int maxX() {
        return origin.originX() + dimX();
    }

    public int maxY() {
        return origin.originY() + dimY();
    }

    public int maxZ() {
        return origin.originZ() + dimZ();
    }

    public static VolumeStamp all() {
        return BOTTOM;
    }

    public static boolean isCellCorner(VolumeStamp stamp) {
        final int stepX = stamp.stepX;
        final int stepY = stamp.stepY;
        final int stepZ = stamp.stepZ;
        return Integer.bitCount(stepX) == 1 && Integer.bitCount(stepY) == 1 && Integer.bitCount(stepZ) == 1 &&
                stepX > 2 && stepY > 4 && stepZ > 2 && stamp.isAligned();
    }

    public static VolumeStamp create(int stepX, int stepY, int stepZ, int sizeX, int sizeY, int sizeZ, CoordinationStamp origin) {
        return new VolumeStamp(stepX, stepY, stepZ, sizeX, sizeY, sizeZ, origin);
    }

    public static VolumeStamp cellify(VolumeStamp stamp, int cellSizeX, int cellSizeY, int cellSizeZ) {
        if (isCellCorner(stamp)) {
            return stamp;
        }
        final var origin = stamp.origin();
        final int baseCellX = Math.floorDiv(origin.originX(), cellSizeX);
        final int baseCellY = Math.floorDiv(origin.originY(), cellSizeY);
        final int baseCellZ = Math.floorDiv(origin.originZ(), cellSizeZ);

        final int maxCellX = Math.ceilDiv(origin.originX(), cellSizeX);
        final int maxCellY = Math.ceilDiv(origin.originY(), cellSizeY);
        final int maxCellZ = Math.ceilDiv(origin.originZ(), cellSizeZ);

        final int sizeX = (maxCellX - baseCellX + 1) + 1;
        final int sizeY = (maxCellY - baseCellY + 1) + 1;
        final int sizeZ = (maxCellZ - baseCellZ + 1) + 1;

        final int baseX = baseCellX * cellSizeX;
        final int baseY = baseCellY * cellSizeY;
        final int baseZ = baseCellZ * cellSizeZ;

        return new VolumeStamp(
                cellSizeX, cellSizeY, cellSizeZ,
                sizeX, sizeY, sizeZ,
                new CoordinationStamp(baseX, baseY, baseZ)
        );
    }
}
