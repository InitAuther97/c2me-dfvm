package com.ishland.c2me.opts.dfc.common.ir.node.hir.volume;

/// Squashed description of the volume sampled by a subtree after any
/// sequence of Slice and Interpolated transformations. The node's memory
/// operand is the original input volume node the transformation is applied
/// to: a {@link DensityVolumeNode}, or an {@link FTPTransformationNode} when
/// the transformation is nested below a FindTopSurface.
///
/// Each axis either samples a single fixed coordinate or a strided range;
/// `-1` marks an unknown size or step.
public record SITransformationNode(Axis x, Axis y, Axis z) implements VolumeNode {

    public static final SITransformationNode UNKNOWN =
            new SITransformationNode(Axis.UNKNOWN, Axis.UNKNOWN, Axis.UNKNOWN);

    /// `value` is the fixed coordinate for a fixed axis; for a range
    /// produced by interpolating a fixed axis it keeps that coordinate as
    /// the cell anchor. Other ranges use 0.
    public record Axis(boolean fixed, int value, int size, int step) {

        public static final Axis UNKNOWN = new Axis(false, 0, -1, -1);

        public static Axis fixed(int value) {
            return new Axis(true, value, -1, -1);
        }

        public static Axis range(int size, int step) {
            return new Axis(false, 0, size, step);
        }
    }
}