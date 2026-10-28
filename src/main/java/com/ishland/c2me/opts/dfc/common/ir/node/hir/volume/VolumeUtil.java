package com.ishland.c2me.opts.dfc.common.ir.node.hir.volume;

import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import net.minecraft.util.math.Direction;

/// Builds symbolic sampling domains while retaining their group dependency.
/// Consecutive Slice/Interpolated transforms compose; FTP stays an identity boundary.
public final class VolumeUtil {

    private VolumeUtil() {
    }

    /// Applies a slice transform to the current volume. The resulting
    /// transformation always points at the original input volume node.
    public static int slice(IrGraph graph, int volume, Direction.Axis axis, int coordinate) {
        return switch (graph.getNode(volume)) {
            case DensityVolumeNode base ->
                    addVolume(graph, volume, fixedAxis(baseSIT(base), axis, coordinate));
            case SITransformationNode sit ->
                    addVolume(graph, baseOf(graph, volume), fixedAxis(sit, axis, coordinate));
            case FTPTransformationNode ignored ->
                    addVolume(graph, volume, fixedAxis(SITransformationNode.UNKNOWN, axis, coordinate));
            default -> throw new IllegalStateException("Not a volume node: " + graph.getNode(volume));
        };
    }

    /// Applies an interpolation transform to the current volume. The
    /// resulting transformation always points at the original input volume
    /// node.
    public static int interpolate(IrGraph graph, int volume, int cellSizeXz, int cellSizeY) {
        return switch (graph.getNode(volume)) {
            case DensityVolumeNode base ->
                    addVolume(graph, volume, cellify(baseSIT(base), cellSizeXz, cellSizeY));
            case SITransformationNode sit ->
                    addVolume(graph, baseOf(graph, volume), cellify(sit, cellSizeXz, cellSizeY));
            case FTPTransformationNode ignored ->
                    addVolume(graph, volume, cellify(SITransformationNode.UNKNOWN, cellSizeXz, cellSizeY));
            default -> throw new IllegalStateException("Not a volume node: " + graph.getNode(volume));
        };
    }

    public static int findTopSurface(IrGraph graph, int volume) {
        final int id = graph.addNode(FTPTransformationNode.INSTANCE);
        graph.getMemory().appendEdge(volume, id);
        return id;
    }

    private static int addVolume(IrGraph graph, int base, SITransformationNode sit) {
        final int id = graph.addNode(sit);
        graph.getMemory().appendEdge(base, id);
        return id;
    }

    /// The original input volume node a transformation was applied to.
    private static int baseOf(IrGraph graph, int transformation) {
        return graph.getMemory().getOperands(transformation).getInt(0);
    }

    private static SITransformationNode baseSIT(DensityVolumeNode base) {
        return new SITransformationNode(
                SITransformationNode.Axis.range(base.sizeX(), base.stepX()),
                SITransformationNode.Axis.range(base.sizeY(), base.stepY()),
                SITransformationNode.Axis.range(base.sizeZ(), base.stepZ()));
    }

    private static SITransformationNode fixedAxis(SITransformationNode sit, Direction.Axis axis, int coordinate) {
        final SITransformationNode.Axis fixed = SITransformationNode.Axis.fixed(coordinate);
        return switch (axis) {
            case X -> new SITransformationNode(fixed, sit.y(), sit.z());
            case Y -> new SITransformationNode(sit.x(), fixed, sit.z());
            case Z -> new SITransformationNode(sit.x(), sit.y(), fixed);
        };
    }

    private static SITransformationNode cellify(SITransformationNode sit, int cellSizeXz, int cellSizeY) {
        return new SITransformationNode(
                cellify(sit.x(), cellSizeXz),
                cellify(sit.y(), cellSizeY),
                cellify(sit.z(), cellSizeXz));
    }

    private static SITransformationNode.Axis cellify(SITransformationNode.Axis axis, int cellSize) {
        if (axis.fixed()) {
            // Interpolation samples the cell corners around the fixed
            // coordinate; keep that coordinate as the cell anchor so
            // different fixed coordinates stay distinct.
            return new SITransformationNode.Axis(false, axis.value(), 2, cellSize);
        }
        return SITransformationNode.Axis.range(-1, cellSize);
    }

}
