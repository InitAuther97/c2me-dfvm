package com.ishland.c2me.opts.dfc.common.tree;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.math.Interval;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.world.gen.densityfunction.DensityFunctionVisitor;
import net.minecraft.world.gen.sampler.Sampler;

public record MarkerDensityFunction(
        int idOfInner
) implements DensityFunction {
    @Override
    public Sampler compile(CompileContext context) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DensityFunction accept(DensityFunctionVisitor visitor) {
        return null;
    }

    @Override
    public Interval getRange() {
        return null;
    }

    @Override
    public @Axes int getVariantAxes() {
        return 0;
    }

    @Override
    public MapCodec<? extends DensityFunction> getCodec() {
        return null;
    }
}
