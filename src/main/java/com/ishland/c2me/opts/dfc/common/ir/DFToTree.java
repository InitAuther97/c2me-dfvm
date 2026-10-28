package com.ishland.c2me.opts.dfc.common.ir;

import com.ishland.c2me.opts.dfc.common.ir.graph.IrGraph;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.RootNode;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.TreeIRNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.group.GroupNode;
import com.ishland.c2me.opts.dfc.common.ir.node.tree.group.IdentifierTagTree;
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
import com.ishland.c2me.opts.dfc.common.ir.node.hir.volume.DensityVolumeNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.volume.FTPTransformationNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.volume.SITransformationNode;
import com.ishland.c2me.opts.dfc.common.ir.node.hir.volume.VolumeUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.util.Identifier;
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
import java.util.Map;

/// Lowers grouped vanilla {@link DensityFunction} trees into a single
/// tree-stage graph.
///
/// Every group gets one {@link GroupNode} and one base
/// {@link DensityVolumeNode} consuming it through a memory edge. Every
/// mapped node consumes its current volume node through a memory edge, so
/// value numbering is automatically scoped to the sampling volume. Slice and
/// Interpolated start an {@link SITransformationNode} for their input;
/// FindTopSurface tags its density input with an
/// {@link FTPTransformationNode}.
///
/// Each input root additionally consumes an {@link IdentifierTagTree}
/// carrying its caller-supplied {@link Identifier}; roots split off at a
/// volume change later carry no identifier so they can deduplicate.
///
/// Nodes are not deduplicated here: every occurrence creates fresh nodes and
/// GVN merges equivalent ones using the volume operands.
public class DFToTree {

    /// Each map is one group from input identifier to density function: its
    /// roots consume the same group node, and each root carries its input's
    /// identifier tag.
    public static GraphContext lower(Collection<? extends Map<Identifier, DensityFunction>> groups) {
        final DFToTree lowering = new DFToTree();
        for (final Map<Identifier, DensityFunction> group : groups) {
            lowering.addGroup(group);
        }
        return lowering.context;
    }

    private final GraphContext context = new GraphContext();
    private final IrGraph graph = context.getIrGraph();

    private DFToTree() {
    }

    /// Creates one group node, one base volume and one root per input; each
    /// input root consumes `[function, group, identifier]` as value operands.
    private void addGroup(Map<Identifier, DensityFunction> functions) {
        final int group = graph.addNode(GroupNode.TAG);
        final int volume = graph.addNode(DensityVolumeNode.UNKNOWN);
        graph.getMemory().appendEdge(group, volume);
        for (final Map.Entry<Identifier, DensityFunction> entry : functions.entrySet()) {
            final int value = mapFunction(entry.getValue(), volume);
            final int identifier = graph.addNode(new IdentifierTagTree(entry.getKey()));
            final int root = graph.addNode(RootNode.INSTANCE);
            graph.getControl().appendEdge(context.getProgramStart(), root);
            graph.getControl().appendEdge(root, context.getProgramEnd());
            graph.getValue().appendEdge(value, root);
            graph.getValue().appendEdge(group, root);
            graph.getValue().appendEdge(identifier, root);
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
                final int innerVolume = VolumeUtil.interpolate(graph, volume, interpolated.cellSizeXz(), interpolated.cellSizeY());
                yield add(volume, new InterpolatedTree(interpolated.cellSizeXz(), interpolated.cellSizeY()),
                        mapFunction(interpolated.input(), innerVolume));
            }
            case SliceDensityFunction slice -> {
                final int innerVolume = VolumeUtil.slice(graph, volume, slice.axis(), slice.coordinate());
                yield add(volume, new SliceTree(slice.axis(), slice.coordinate()),
                        mapFunction(slice.input(), innerVolume));
            }
            case FindTopSurfaceDensityFunction findTopSurface -> {
                // Only the searched density lives in the dynamic column
                // volume; the upper bound is sampled per input of the
                // transformation in the enclosing volume.
                final int densityVolume = VolumeUtil.findTopSurface(graph, volume);
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

    private int add(int volume, TreeIRNode node, int... valueOperands) {
        final int id = graph.addNode(node);
        for (final int operand : valueOperands) {
            graph.getValue().appendEdge(operand, id);
        }
        graph.getMemory().appendEdge(volume, id);
        return id;
    }
}