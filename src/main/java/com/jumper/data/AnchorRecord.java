package com.jumper.data;

import com.jumper.JumperRules;

/**
 * What Jumper remembers about one anchor: its tier and its stored power.
 * <p>
 * Power only drains while the anchor's chunk is running (its block entity takes off the upkeep once a second, and block
 * entities only run in running chunks). An unloaded base uses nothing, and its anchor counts as tier 1 until it's
 * loaded again.
 */
public final class AnchorRecord {
    private int tier;
    /** Stored FE. */
    private long energy;
    /** Saved spots on this anchor (all linked players, each counted up to the tier's slots). Not saved: recounted. */
    private int spots;

    public AnchorRecord(int tier) {
        this.tier = JumperRules.clampTier(tier);
    }

    AnchorRecord(int tier, long energy) {
        this(tier);
        this.energy = Math.max(0, energy);
    }

    /** The anchor's real tier (its upgrades), whether or not it has power. */
    public int tier() {
        return tier;
    }

    void setTier(int tier) {
        this.tier = JumperRules.clampTier(tier);
    }

    public long energy() {
        return energy;
    }

    public int spots() {
        return spots;
    }

    void setSpots(int spots) {
        this.spots = Math.max(0, spots);
    }

    /** FE per tick this anchor uses while running. */
    public long rate() {
        return JumperRules.upkeep(tier, spots);
    }

    /** Most FE it can hold: {@link JumperRules#bufferTicks()} worth of its current upkeep. */
    public long capacity() {
        return rate() * JumperRules.bufferTicks();
    }

    /** True if this anchor needs power at all (tier 2 and up, with upkeep above 0). */
    public boolean needsPower() {
        return rate() > 0;
    }

    /**
     * Takes off this many ticks of upkeep.
     *
     * @return true if the stored power changed
     */
    boolean drain(long ticks) {
        long rate = rate();
        if (ticks <= 0 || rate == 0 || energy == 0) {
            return false;
        }
        // ticks * rate could overflow for huge values, so compare by division first.
        energy = ticks > energy / rate ? 0 : Math.max(0, energy - ticks * rate);
        return true;
    }

    void setEnergy(long energy) {
        this.energy = Math.max(0, Math.min(energy, capacity()));
    }

    /** After the upkeep went down (fewer spots, lower tier), drop whatever no longer fits. */
    void clampToCapacity() {
        if (energy > capacity()) {
            energy = capacity();
        }
    }

    /**
     * Stored FE as the energy capability reports it (an int). A tier 2+ buffer can hold more than an int, so above
     * that both numbers are scaled down together: the fill level stays right, and "stored &lt; max" stays true exactly
     * while there is room, for pipes and machines that check that before pushing power in.
     */
    public int reportedEnergy() {
        long capacity = capacity();
        if (capacity <= Integer.MAX_VALUE) {
            return (int) Math.max(0, Math.min(energy, capacity));
        }
        if (energy >= capacity) {
            return Integer.MAX_VALUE;
        }
        double scaled = (double) energy / (double) capacity * Integer.MAX_VALUE;
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE - 1L, (long) scaled));
    }

    /** Capacity as the energy capability reports it: the real capacity, or the int limit when it is bigger. */
    public int reportedCapacity() {
        return (int) Math.min(Integer.MAX_VALUE, capacity());
    }
}
