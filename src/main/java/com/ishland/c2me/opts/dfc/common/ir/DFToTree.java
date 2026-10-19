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
import it.unimi.dsi.fastutil.ints.IntArrayList;
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
import java.util.IdentityHashMap;
import java.util.Map;

/// Lowers grouped vanilla {@link DensityFunction} trees into a single
/// tree-stage graph. Nodes are deduplicated by identity within each group
/// only. Every group becomes one {@link GroupTagTree} node, and every root
/// in the group consumes that group node together with its mapped function
/// value.
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

    /// Creates one group node and one root per function; each root consumes
    /// `[function, group]` as value operands.
    private void addGroup(Collection<? extends DensityFunction> functions) {
        final int group = graph.addNode(GroupTagTree.TAG);
        final Map<DensityFunction, Integer> mapped = new IdentityHashMap<>();
        for (final DensityFunction function : functions) {
            final int value = mapFunction(function, mapped);
            final int root = graph.addNode(RootNode.INSTANCE);
            graph.getControl().appendEdge(context.getProgramStart(), root);
            graph.getControl().appendEdge(root, context.getProgramEnd());
            graph.getValue().appendEdge(value, root);
            graph.getValue().appendEdge(group, root);
        }
    }

    private int mapFunction(DensityFunction function, Map<DensityFunction, Integer> mapped) {
        final Integer existing = mapped.get(function);
        if (existing != null) {
            return existing;
        }
        final int id = switch (function) {
            case DensityFunctionTypes.RegistryEntryHolder holder ->
                    add(RegistryEntryHolderTree.INSTANCE, mapFunction(holder.function().value(), mapped));
            case ConstantDensityFunction constant ->
                    add(new ConstantTree(constant.value()));
            case BinaryOperationDensityFunction binary ->
                    add(new BinaryOperationTree(binary.type()),
                            mapFunction(binary.left(), mapped), mapFunction(binary.right(), mapped));
            case UnaryOperationDensityFunction unary ->
                    add(new UnaryOperationTree(unary.type()), mapFunction(unary.input(), mapped));
            case ClampDensityFunction clamp ->
                    add(new ClampTree(clamp.min(), clamp.max()), mapFunction(clamp.input(), mapped));
            case BlendDensityFunction blend ->
                    add(switch (blend) {
                        case BLEND_ALPHA -> BlendDensityTree.BLEND_ALPHA;
                        case BLEND_OFFSET -> BlendDensityTree.BLEND_OFFSET;
                        case BEARDIFIER -> BlendDensityTree.BEARDIFIER;
                    });
            case BlendDensityDensityFunction blend ->
                    add(BlendDensityInputTree.INSTANCE, mapFunction(blend.input(), mapped));
            case CacheDensityFunction cache ->
                    add(CacheTree.INSTANCE, mapFunction(cache.input(), mapped));
            case NoiseDensityFunction noise ->
                    add(new NoiseTree(noise.noise(), noise.xzScale(), noise.yScale()),
                            mapFunction(noise.shiftX(), mapped),
                            mapFunction(noise.shiftY(), mapped),
                            mapFunction(noise.shiftZ(), mapped));
            case EndOuterIslandsDensityFunction ignored ->
                    add(EndOuterIslandsTree.INSTANCE);
            case DistanceToPointDensityFunction distance ->
                    add(new DistanceToPointTree(distance.point(), distance.metric()));
            case GradientDensityFunction gradient ->
                    add(new GradientTree(gradient.axis(), gradient.tiling(),
                            gradient.fromCoordinate(), gradient.toCoordinate(),
                            gradient.fromValue(), gradient.toValue()));
            case OffsetDensityFunction.Shift shift ->
                    add(new OffsetTree.Shift(shift.offsetNoise()));
            case OffsetDensityFunction.ShiftA shiftA ->
                    add(new OffsetTree.ShiftA(shiftA.offsetNoise()));
            case OffsetDensityFunction.ShiftB shiftB ->
                    add(new OffsetTree.ShiftB(shiftB.offsetNoise()));
            case RoundingDensityFunction rounding ->
                    add(new RoundingTree(rounding.type()),
                            mapFunction(rounding.input(), mapped), mapFunction(rounding.multiple(), mapped));
            case PowerDensityFunction power ->
                    add(PowerTree.INSTANCE,
                            mapFunction(power.base(), mapped), mapFunction(power.exponent(), mapped));
            case SplineDensityFunction spline -> {
                final IntArrayList locationFunctions = new IntArrayList();
                spline.spline().visit(wrapper ->
                        locationFunctions.add(mapFunction(wrapper.function(), mapped)));
                yield add(new SplineTree(spline.spline()), locationFunctions.toIntArray());
            }
            case LerpDensityFunction lerp ->
                    add(LerpTree.INSTANCE,
                            mapFunction(lerp.alpha(), mapped),
                            mapFunction(lerp.first(), mapped),
                            mapFunction(lerp.second(), mapped));
            case InterpolatedDensityFunction interpolated ->
                    add(new InterpolatedTree(interpolated.cellSizeXz(), interpolated.cellSizeY()),
                            mapFunction(interpolated.input(), mapped));
            case SliceDensityFunction slice ->
                    add(new SliceTree(slice.axis(), slice.coordinate()),
                            mapFunction(slice.input(), mapped));
            case FindTopSurfaceDensityFunction findTopSurface ->
                    add(new FindTopSurfaceTree(findTopSurface.lowerBound(), findTopSurface.cellHeight()),
                            mapFunction(findTopSurface.density(), mapped),
                            mapFunction(findTopSurface.upperBound(), mapped));
            case RangeChoiceDensityFunction rangeChoice ->
                    add(new RangeChoiceTree(rangeChoice.minInclusive(), rangeChoice.maxExclusive()),
                            mapFunction(rangeChoice.input(), mapped),
                            mapFunction(rangeChoice.whenInRange(), mapped),
                            mapFunction(rangeChoice.whenOutOfRange(), mapped));
            case IntervalSelectDensityFunction intervalSelect -> {
                final IntArrayList operands = new IntArrayList();
                operands.add(mapFunction(intervalSelect.input(), mapped));
                for (final DensityFunction branch : intervalSelect.functions()) {
                    operands.add(mapFunction(branch, mapped));
                }
                yield add(new IntervalSelectTree(intervalSelect.thresholds()), operands.toIntArray());
            }
            case BlendedNoiseSampler blendedNoise ->
                    add(new BlendedNoiseTree(blendedNoise.xzScale(), blendedNoise.yScale(),
                            blendedNoise.xzFactor(), blendedNoise.yFactor(),
                            blendedNoise.smearScaleMultiplier()));
            default -> throw new UnsupportedOperationException(
                    "Unsupported density function type: " + function.getClass().getName());
        };
        mapped.put(function, id);
        return id;
    }

    private int add(TreeIRNode node, int... valueOperands) {
        final int id = graph.addNode(node);
        for (final int operand : valueOperands) {
            graph.getValue().appendEdge(operand, id);
        }
        return id;
    }
}