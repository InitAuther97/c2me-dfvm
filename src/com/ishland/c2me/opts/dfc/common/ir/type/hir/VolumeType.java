package com.ishland.c2me.opts.dfc.common.ir.type.hir;

import com.ishland.c2me.opts.dfc.common.ir.type.IrType;

import java.util.Objects;

public final class VolumeType extends IrType {
    private final boolean aligned;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int stepX;
    private final int stepY;
    private final int stepZ;

    public VolumeType(
            boolean aligned,
            int sizeX, int sizeY, int sizeZ,
            int stepX, int stepY, int stepZ
    ) {
        this.aligned = aligned;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.stepX = stepX;
        this.stepY = stepY;
        this.stepZ = stepZ;
    }

    public boolean aligned() {
        return aligned;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public int stepX() {
        return stepX;
    }

    public int stepY() {
        return stepY;
    }

    public int stepZ() {
        return stepZ;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (VolumeType) obj;
        return this.aligned == that.aligned &&
                this.sizeX == that.sizeX &&
                this.sizeY == that.sizeY &&
                this.sizeZ == that.sizeZ &&
                this.stepX == that.stepX &&
                this.stepY == that.stepY &&
                this.stepZ == that.stepZ;
    }

    @Override
    public int hash() {
        return Objects.hash(aligned, sizeX, sizeY, sizeZ, stepX, stepY, stepZ);
    }

    @Override
    protected IrType xmeet(IrType another) {
        return null;
    }

    @Override
    protected IrType dual() {
        return null;
    }

    @Override
    public boolean isDetermined() {
        return false;
    }

    @Override
    protected boolean isSubType(IrType another) {
        return false;
    }

    @Override
    public IrType top() {
        return null;
    }

    @Override
    public String toString() {
        return "VolumeType[" +
                "aligned=" + aligned + ", " +
                "sizeX=" + sizeX + ", " +
                "sizeY=" + sizeY + ", " +
                "sizeZ=" + sizeZ + ", " +
                "stepX=" + stepX + ", " +
                "stepY=" + stepY + ", " +
                "stepZ=" + stepZ + ']';
    }

}
