package com.jumper.block;

import com.jumper.JumperRules;
import com.jumper.data.AnchorKey;
import com.jumper.data.AnchorRecord;
import com.jumper.data.JumperData;
import com.jumper.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * The anchor's block entity. It saves nothing (the power is kept in {@link JumperData}, so jumps never need this chunk
 * loaded). It does two small jobs while the chunk is running: lets cables and generators push FE in through the
 * standard energy capability, and takes off the upkeep once a second. Block entities don't run in unloaded or
 * edge-of-loaded chunks, so an unloaded base uses no power.
 */
public class AnchorBlockEntity extends BlockEntity {
    private final IEnergyStorage energyPort = new EnergyPort();
    /** Worked out once: cables ask every tick, so nothing is allocated per call. */
    private AnchorKey key;
    /** Spreads anchors over the second so they don't all do their upkeep on the same tick. */
    private final int phase;
    /** The tier last seen in {@link JumperData}, for the drop when the block is broken (its entry is gone by then). */
    private int lastTier = JumperRules.MIN_TIER;
    /**
     * The tier written on the item this anchor was placed from. Not saved: only read while it's being placed, because
     * when a player places a block the loader runs {@code onPlace} after {@code setPlacedBy}.
     */
    private int placedTier = JumperRules.MIN_TIER;

    public AnchorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.JUMPER_ANCHOR.get(), pos, state);
        this.phase = Math.floorMod(pos.hashCode(), DRAIN_INTERVAL);
    }

    /** Ticks between upkeep payments (20 = once a second). */
    public static final int DRAIN_INTERVAL = 20;

    /** Server only, while the chunk is running: once a second, pay a second of upkeep. Nothing else per tick. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, AnchorBlockEntity anchorEntity) {
        if ((level.getGameTime() + anchorEntity.phase) % DRAIN_INTERVAL != 0) {
            return;
        }
        JumperData data = anchorEntity.data();
        AnchorRecord anchor = anchorEntity.record(data);
        if (anchor != null) {
            data.drain(anchor, DRAIN_INTERVAL);
            // The look follows the saved tier: catches changes made while the chunk was unloaded, and anchors from
            // before the block showed its tier.
            if (state.getValue(JumperAnchorBlock.TIER) != JumperRules.clampTier(anchor.tier())) {
                JumperAnchorBlock.showTier(level, pos, anchor.tier());
            }
        }
    }

    /** What the energy capability hands out, on every side. */
    public IEnergyStorage energyPort() {
        return energyPort;
    }

    public int placedTier() {
        return placedTier;
    }

    public void setPlacedTier(int tier) {
        placedTier = JumperRules.clampTier(tier);
    }

    /** Notes the anchor's current tier (called just before the block is mined, and after placing). */
    public void rememberTier() {
        record(data());
    }

    /** The tier the dropped anchor item should carry: the live one if the anchor is still listed, else the last seen. */
    public int dropTier() {
        record(data());
        return lastTier;
    }

    /** The anchor's saved data, or null on the client or if it isn't listed. */
    private AnchorRecord record(JumperData data) {
        if (data == null) {
            return null;
        }
        if (key == null) {
            key = JumperData.key(level, worldPosition);
        }
        AnchorRecord anchor = data.anchor(key);
        if (anchor != null) {
            lastTier = anchor.tier();
        }
        return anchor;
    }

    private JumperData data() {
        return level instanceof ServerLevel serverLevel ? JumperData.get(serverLevel.getServer()) : null;
    }

    /** Accepts FE, never gives it back. */
    private final class EnergyPort implements IEnergyStorage {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            JumperData data = data();
            AnchorRecord anchor = record(data);
            return anchor == null ? 0 : data.receiveEnergy(anchor, toReceive, simulate);
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            JumperData data = data();
            AnchorRecord anchor = record(data);
            return anchor == null ? 0 : anchor.reportedEnergy();
        }

        @Override
        public int getMaxEnergyStored() {
            AnchorRecord anchor = record(data());
            return anchor == null ? 0 : anchor.reportedCapacity();
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            AnchorRecord anchor = record(data());
            return anchor != null && anchor.capacity() > 0;
        }
    }
}
