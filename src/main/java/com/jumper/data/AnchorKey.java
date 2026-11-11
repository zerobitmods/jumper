package com.jumper.data;

import net.minecraft.core.BlockPos;

/** Where an anchor is: its dimension id (like "minecraft:overworld") and its block position packed into a long. */
public record AnchorKey(String dimension, long pos) {

    public BlockPos blockPos() {
        return BlockPos.of(pos);
    }
}
