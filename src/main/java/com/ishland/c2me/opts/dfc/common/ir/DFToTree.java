package com.ishland.c2me.opts.dfc.common.ir;

import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.RootNode;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.TreeIRNode;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.group.GroupTagTree;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror.BinaryOperationTree;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror.BlendDensityInputTree;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror.BlendDensityTree;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror.BlendedNoiseTree;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror.CacheTree;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror.ClampTree;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror.ConstantTree;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror.DistanceToPointTree;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror.EndOuterIslandsTree;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror.FindTopSurfaceTree;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror.GradientTree;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror.InterpolatedTree;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror.IntervalSelectTree;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror.LerpTree;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror.NoiseTree;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror.OffsetTree;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror.PowerTree;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror.RangeChoiceTree;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror.RegistryEntryHolderTree;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror.RoundingTree;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror.SliceTree;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror.SplineTree;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.mirror.UnaryOperationTree;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.volume.DensityVolumeNode;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.volume.FTPTransformationTree;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.volume.SITransformationTree;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.noise.BlendedNoiseSampler;
import net.minecraft.world.gen.densityfunction.BinaryOperationDensityFunction;
import net.minecraft.world.gen.densityfunction.BlendDensityDensityFunction;
import net.minecraft.world.gen.densityfunction.BlendDensityFunction;
import net.minecraft.world.gen.densityfunction.CacheDensityFunction;
import net.minecraft.world.gen.densityfunction.ClampDensityFunction;
import net.minecraft.world.gen.densityfunction.ConstantDensityFunction;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes;
import net.minecraft.world.gen.densityfunction.DistanceToPointDensityFunction;
import net.minecraft.world.gen.densityfunction.EndOuterIslandsDensityFunction;
import net.minecraft.world.gen.densityfunction.FindTopSurfaceDensityFunction;
import net.minecraft.world.gen.densityfunction.GradientDensityFunction;
import net.minecraft.world.gen.densityfunction.InterpolatedDensityFunction;
import net.minecraft.world.gen.densityfunction.IntervalSelectDensityFunction;
import net.minecraft.world.gen.densityfunction.LerpDensityFunction;
import net.minecraft.world.gen.densityfunction.NoiseDensityFunction;
import net.minecraft.world.gen.densityfunction.OffsetDensityFunction;
import net.minecraft.world.gen.densityfunction.PowerDensityFunction;
import net.minecraft.world.gen.densityfunction.RangeChoiceDensityFunction;
import net.minecraft.world.gen.densityfunction.RoundingDensityFunction;
import net.minecraft.world.gen.densityfunction.SliceDensityFunction;
import net.minecraft.world.gen.densityfunction.SplineDensityFunction;
import net.minecraft.world.gen.densityfunction.UnaryOperationDensityFunction;

import java.util.Collection;

/// Lowers grouped vanilla {@link DensityFunction} trees into a single
/// tree-stage graph.
///
/// Every group gets one {@link GroupTagTree} and one base
/// {@link DensityVolumeNode} consuming it through a memory edge. Every
/// mapped node consumes its current volume node through a memory edge, so
/// value numbering is automatically scoped to the sampling volume. Slice and
/// Interpolated start an {@link SITransformationTree} for their input;
/// FindTopSurface tags its density input with an
/// {@link FTPTransformationTree}.
///
/// Nodes are not deduplicated here: every occurrence creates fresh nodes and
/// GVN merges equivalent ones using the volume operands.
public class DFToTree {

    /// Each inner collection is one group: its roots consume the same group
    /// node.
    public static GraphContext lower(Collection<? extends Collection<? extends DensityFunction>> groups) {
        final DFToTree lowering = new DFToTree();
        for (final Collection<? extends DensityFunction> group : groups) {
            lowering.addGroup(group);
        }
        return lowering.context;
    }

    private final GraphContext context = new GraphContext();
    private final IrGraph graph = context.getIrGraph();

    private DFToTree() {
    }

    /// Creates one group node, one base volume and one root per function;
    /// each root consumes `[function, group]` as value operands.
    private void addGroup(Collection<? extends DensityFunction> functions) {
        final int group = graph.addNode(GroupTagTree.TAG);
        final int volume = graph.addNode(DensityVolumeNode.UNKNOWN);
        graph.getMemory().appendEdge(group, volume);
        for (final DensityFunction function : functions) {
            final int value = mapFunction(function, volume);
            final int root = graph.addNode(RootNode.INSTANCE);
            graph.getControl().appendEdge(context.getProgramStart(), root);
            graph.getControl().appendEdge(root, context.getProgramEnd());
            graph.getValue().appendEdge(value, root);
            graph.getValue().appendEdge(group, root);
        }
    }

    private int mapFunction(DensityFunction function, int volume) {
        return switch (function) {
            case DensityFunctionTypes.RegistryEntryHolder holder ->
                    add(volume, RegistryEntryHolderTree.INSTANCE, mapFunction(holder.function().value(), volume));
            case ConstantDensityFunction constant ->
                    add(volume, new ConstantTree(constant.value()));
            case BinaryOperationDensityFunction binary ->
                    add(volume, new BinaryOperationTree(binary.type()),
                            mapFunction(binary.left(), volume), mapFunction(binary.right(), volume));
            case UnaryOperationDensityFunction unary ->
                    add(volume, new UnaryOperationTree(unary.type()), mapFunction(unary.input(), volume));
            case ClampDensityFunction clamp ->
                    add(volume, new ClampTree(clamp.min(), clamp.max()), mapFunction(clamp.input(), volume));
            case BlendDensityFunction blend ->
                    add(volume, switch (blend) {
                        case BLEND_ALPHA -> BlendDensityTree.BLEND_ALPHA;
                        case BLEND_OFFSET -> BlendDensityTree.BLEND_OFFSET;
                        case BEARDIFIER -> BlendDensityTree.BEARDIFIER;
                    });
            case BlendDensityDensityFunction blend ->
                    add(volume, BlendDensityInputTree.INSTANCE, mapFunction(blend.input(), volume));
            case CacheDensityFunction cache ->
                    add(volume, CacheTree.INSTANCE, mapFunction(cache.input(), volume));
            case NoiseDensityFunction noise ->
                    add(volume, new NoiseTree(noise.noise(), noise.xzScale(), noise.yScale()),
                            mapFunction(noise.shiftX(), volume),
                            mapFunction(noise.shiftY(), volume),
                            mapFunction(noise.shiftZ(), volume));
            case EndOuterIslandsDensityFunction ignored ->
                    add(volume, EndOuterIslandsTree.INSTANCE);
            case DistanceToPointDensityFunction distance ->
                    add(volume, new DistanceToPointTree(distance.point(), distance.metric()));
            case GradientDensityFunction gradient ->
                    add(volume, new GradientTree(gradient.axis(), gradient.tiling(),
                            gradient.fromCoordinate(), gradient.toCoordinate(),
                            gradient.fromValue(), gradient.toValue()));
            case OffsetDensityFunction.Shift shift ->
                    add(volume, new OffsetTree.Shift(shift.offsetNoise()));
            case OffsetDensityFunction.ShiftA shiftA ->
                    add(volume, new OffsetTree.ShiftA(shiftA.offsetNoise()));
            case OffsetDensityFunction.ShiftB shiftB ->
                    add(volume, new OffsetTree.ShiftB(shiftB.offsetNoise()));
            case RoundingDensityFunction rounding ->
                    add(volume, new RoundingTree(rounding.type()),
                            mapFunction(rounding.input(), volume), mapFunction(rounding.multiple(), volume));
            case PowerDensityFunction power ->
                    add(volume, PowerTree.INSTANCE,
                            mapFunction(power.base(), volume), mapFunction(power.exponent(), volume));
            case SplineDensityFunction spline -> {
                final IntArrayList locationFunctions = new IntArrayList();
                spline.spline().visit(wrapper ->
                        locationFunctions.add(mapFunction(wrapper.function(), volume)));
                yield add(volume, new SplineTree(spline.spline()), locationFunctions.toIntArray());
            }
            case LerpDensityFunction lerp ->
                    add(volume, LerpTree.INSTANCE,
                            mapFunction(lerp.alpha(), volume),
                            mapFunction(lerp.first(), volume),
                            mapFunction(lerp.second(), volume));
            case InterpolatedDensityFunction interpolated -> {
                final int innerVolume = cellifyVolume(volume, interpolated.cellSizeXz(), interpolated.cellSizeY());
                yield add(volume, new InterpolatedTree(interpolated.cellSizeXz(), interpolated.cellSizeY()),
                        mapFunction(interpolated.input(), innerVolume));
            }
            case SliceDensityFunction slice -> {
                final int innerVolume = sliceVolume(volume, slice.axis(), slice.coordinate());
                yield add(volume, new SliceTree(slice.axis(), slice.coordinate()),
                        mapFunction(slice.input(), innerVolume));
            }
            case FindTopSurfaceDensityFunction findTopSurface -> {
                // Only the searched density lives in the dynamic column
                // volume; the upper bound is sampled per input of the
                // transformation in the enclosing volume.
                final int densityVolume = findTopSurfaceVolume(volume);
                yield add(volume, new FindTopSurfaceTree(findTopSurface.lowerBound(), findTopSurface.cellHeight()),
                        mapFunction(findTopSurface.density(), densityVolume),
                        mapFunction(findTopSurface.upperBound(), volume));
            }
            case RangeChoiceDensityFunction rangeChoice ->
                    add(volume, new RangeChoiceTree(rangeChoice.minInclusive(), rangeChoice.maxExclusive()),
                            mapFunction(rangeChoice.input(), volume),
                            mapFunction(rangeChoice.whenInRange(), volume),
                            mapFunction(rangeChoice.whenOutOfRange(), volume));
            case IntervalSelectDensityFunction intervalSelect -> {
                final IntArrayList operands = new IntArrayList();
                operands.add(mapFunction(intervalSelect.input(), volume));
                for (final DensityFunction branch : intervalSelect.functions()) {
                    operands.add(mapFunction(branch, volume));
                }
                yield add(volume, new IntervalSelectTree(intervalSelect.thresholds()), operands.toIntArray());
            }
            case BlendedNoiseSampler blendedNoise ->
                    add(volume, new BlendedNoiseTree(blendedNoise.xzScale(), blendedNoise.yScale(),
                            blendedNoise.xzFactor(), blendedNoise.yFactor(),
                            blendedNoise.smearScaleMultiplier()));
            default -> throw new UnsupportedOperationException(
                    "Unsupported density function type: " + function.getClass().getName());
        };
    }

    /// Applies a slice transform to the current volume. The resulting
    /// transformation always points at the original input volume node.
    private int sliceVolume(int volume, Direction.Axis axis, int coordinate) {
        return switch (graph.getNode(volume)) {
            case DensityVolumeNode base ->
                    addVolume(volume, fixedAxis(baseSIT(base), axis, coordinate));
            case SITransformationTree sit ->
                    addVolume(baseOf(volume), fixedAxis(sit, axis, coordinate));
            case FTPTransformationTree ignored ->
                    addVolume(volume, fixedAxis(SITransformationTree.UNKNOWN, axis, coordinate));
            default -> throw new IllegalStateException("Not a volume node: " + graph.getNode(volume));
        };
    }

    /// Applies an interpolation transform to the current volume. The
    /// resulting transformation always points at the original input volume
    /// node.
    private int cellifyVolume(int volume, int cellSizeXz, int cellSizeY) {
        return switch (graph.getNode(volume)) {
            case DensityVolumeNode base ->
                    addVolume(volume, cellify(baseSIT(base), cellSizeXz, cellSizeY));
            case SITransformationTree sit ->
                    addVolume(baseOf(volume), cellify(sit, cellSizeXz, cellSizeY));
            case FTPTransformationTree ignored ->
                    addVolume(volume, cellify(SITransformationTree.UNKNOWN, cellSizeXz, cellSizeY));
            default -> throw new IllegalStateException("Not a volume node: " + graph.getNode(volume));
        };
    }

    private int findTopSurfaceVolume(int volume) {
        final int id = graph.addNode(FTPTransformationTree.INSTANCE);
        graph.getMemory().appendEdge(volume, id);
        return id;
    }

    private int addVolume(int base, SITransformationTree sit) {
        final int id = graph.addNode(sit);
        graph.getMemory().appendEdge(base, id);
        return id;
    }

    /// The original input volume node a transformation was applied to.
    private int baseOf(int transformation) {
        return graph.getMemory().getOperands(transformation).getInt(0);
    }

    private static SITransformationTree baseSIT(DensityVolumeNode base) {
        return new SITransformationTree(
                SITransformationTree.Axis.range(base.sizeX(), base.stepX()),
                SITransformationTree.Axis.range(base.sizeY(), base.stepY()),
                SITransformationTree.Axis.range(base.sizeZ(), base.stepZ()));
    }

    private static SITransformationTree fixedAxis(SITransformationTree sit, Direction.Axis axis, int coordinate) {
        final SITransformationTree.Axis fixed = SITransformationTree.Axis.fixed(coordinate);
        return switch (axis) {
            case X -> new SITransformationTree(fixed, sit.y(), sit.z());
            case Y -> new SITransformationTree(sit.x(), fixed, sit.z());
            case Z -> new SITransformationTree(sit.x(), sit.y(), fixed);
        };
    }

    private static SITransformationTree cellify(SITransformationTree sit, int cellSizeXz, int cellSizeY) {
        return new SITransformationTree(
                cellify(sit.x(), cellSizeXz),
                cellify(sit.y(), cellSizeY),
                cellify(sit.z(), cellSizeXz));
    }

    private static SITransformationTree.Axis cellify(SITransformationTree.Axis axis, int cellSize) {
        if (axis.fixed()) {
            // Interpolation samples the cell corners around the fixed
            // coordinate; keep that coordinate as the cell anchor so
            // different fixed coordinates stay distinct.
            return new SITransformationTree.Axis(false, axis.value(), 2, cellSize);
        }
        return SITransformationTree.Axis.range(-1, cellSize);
    }

    private int add(int volume, TreeIRNode node, int... valueOperands) {
        final int id = graph.addNode(node);
        for (final int operand : valueOperands) {
            graph.getValue().appendEdge(operand, id);
        }
        graph.getMemory().appendEdge(volume, id);
        return id;
    }
}