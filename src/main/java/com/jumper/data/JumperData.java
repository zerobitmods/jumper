package com.jumper.data;

import com.jumper.Jumper;
import com.jumper.JumperRules;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything Jumper saves, in one small file for the whole world (data/jumper.dat in the overworld's folder): the list
 * of anchors in every dimension, and each player's link, saved spots and last jump-from place. Kept in memory while the server runs, so looking something up
 * never loads a chunk. Only ever touched from the server thread.
 */
public final class JumperData extends SavedData {
    private static final String FILE_NAME = "jumper";
    private final Map<AnchorKey, AnchorRecord> anchors = new HashMap<>();
    private final Map<UUID, PlayerRecord> players = new HashMap<>();
    public static JumperData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(JumperData::load, JumperData::new, FILE_NAME);
    }

    /** The id Jumper stores for a dimension, like "minecraft:overworld". */
    public static String dimensionId(Level level) {
        return level.dimension().location().toString();
    }

    public static AnchorKey key(Level level, BlockPos pos) {
        return new AnchorKey(dimensionId(level), pos.asLong());
    }

    // ---- anchors ----

    /** The anchor at this spot, added (as tier 1) if it isn't listed yet. */
    public AnchorRecord ensureAnchor(Level level, BlockPos pos) {
        AnchorKey key = key(level, pos);
        AnchorRecord anchor = anchors.get(key);
        if (anchor == null) {
            anchor = new AnchorRecord(JumperRules.MIN_TIER);
            anchors.put(key, anchor);
            recount(key);
            setDirty();
        }
        return anchor;
    }

    /**
     * A newly placed anchor, replacing any stale entry left at this spot. Starts at the tier its item carried (1 for a
     * crafted anchor), with no power.
     */
    public void placeAnchor(Level level, BlockPos pos, int tier) {
        AnchorKey key = key(level, pos);
        anchors.put(key, new AnchorRecord(tier));
        recount(key); // players can still be linked to an anchor that used to stand here
        setDirty();
    }

    public void removeAnchor(Level level, BlockPos pos) {
        removeAnchor(key(level, pos));
    }

    public void removeAnchor(AnchorKey key) {
        if (anchors.remove(key) != null) {
            setDirty();
        }
    }

    /**
     * Sets a listed anchor's tier (1 to 4).
     *
     * @return false if there's no anchor listed there
     */
    public boolean setTier(AnchorKey key, int tier) {
        AnchorRecord anchor = anchors.get(key);
        if (anchor == null) {
            return false;
        }
        anchor.setTier(tier);
        recount(key);
        setDirty();
        return true;
    }

    // ---- power ----

    /** Takes off this many ticks of upkeep. Called by the anchor's block entity while its chunk is running. */
    public void drain(AnchorRecord anchor, long ticks) {
        if (anchor.drain(ticks)) {
            setDirty();
        }
    }

    /**
     * Power arriving from a cable or generator.
     *
     * @return how much FE was (or, when simulating, would be) taken
     */
    public int receiveEnergy(AnchorRecord anchor, int amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        long room = anchor.capacity() - anchor.energy();
        if (room <= 0) {
            return 0;
        }
        int accepted = (int) Math.min(room, amount);
        if (!simulate) {
            anchor.setEnergy(anchor.energy() + accepted);
            setDirty();
        }
        return accepted;
    }

    /** Operators and testing: sets the stored power (kept between 0 and the capacity). */
    public void setEnergy(AnchorRecord anchor, long energy) {
        anchor.setEnergy(energy);
        setDirty();
    }

    /**
     * Counts the spots saved on this anchor by every player linked to it (each up to the anchor's slots), since the
     * upkeep depends on it. Only runs when a link, spot or tier changes.
     */
    private void recount(AnchorKey key) {
        AnchorRecord anchor = key == null ? null : anchors.get(key);
        if (anchor == null) {
            return;
        }
        int slots = JumperRules.slots(anchor.tier());
        int count = 0;
        for (PlayerRecord record : players.values()) {
            if (key.equals(record.link())) {
                count += Math.min(record.spots().size(), slots);
            }
        }
        anchor.setSpots(count);
        anchor.clampToCapacity();
    }

    /**
     * The server config changed (slots and upkeep may have): recount every anchor and drop stored power that no longer
     * fits. Lowering the buffer or the upkeep can therefore lose some stored FE; nothing else changes.
     */
    public void configChanged() {
        recountAll();
        for (AnchorRecord anchor : anchors.values()) {
            anchor.clampToCapacity();
        }
        setDirty();
    }

    /** After loading: recount every anchor's spots in one pass. */
    private void recountAll() {
        for (AnchorRecord anchor : anchors.values()) {
            anchor.setSpots(0);
        }
        for (PlayerRecord record : players.values()) {
            AnchorRecord anchor = record.link() == null ? null : anchors.get(record.link());
            if (anchor != null) {
                anchor.setSpots(anchor.spots() + Math.min(record.spots().size(), JumperRules.slots(anchor.tier())));
            }
        }
    }

    /** The listed anchor, or null. */
    public AnchorRecord anchor(AnchorKey key) {
        return key == null ? null : anchors.get(key);
    }

    public int anchorCount() {
        return anchors.size();
    }

    // ---- players ----

    /** This player's record, or null if Jumper has nothing saved for them. */
    public PlayerRecord player(UUID id) {
        return players.get(id);
    }

    private PlayerRecord playerOrCreate(UUID id) {
        return players.computeIfAbsent(id, ignored -> new PlayerRecord());
    }

    /**
     * Links the player to the anchor at this spot.
     *
     * @return true if the link changed, false if they were already linked to this anchor
     */
    public boolean link(UUID playerId, Level level, BlockPos pos) {
        AnchorKey key = key(level, pos);
        PlayerRecord record = playerOrCreate(playerId);
        if (key.equals(record.link())) {
            return false;
        }
        AnchorKey old = record.link();
        record.setLink(key);
        recount(old);
        recount(key);
        setDirty();
        return true;
    }

    /** Adds a spot to the end of the player's list. The caller checks names and the slot limit. */
    public void addSpot(PlayerRecord record, Spot spot) {
        record.spotList().add(spot);
        recount(record.link());
        setDirty();
    }

    /** Puts a spot back at this place in the player's list (Undo of a delete). The caller checks names and the index. */
    public void insertSpot(PlayerRecord record, int index, Spot spot) {
        record.spotList().add(index, spot);
        recount(record.link());
        setDirty();
    }

    public Spot removeSpot(PlayerRecord record, int index) {
        Spot removed = record.spotList().remove(index);
        recount(record.link());
        setDirty();
        return removed;
    }

    /** Puts a changed copy of a spot (new name or picture) in its place. The caller checks the index and the name. */
    public void replaceSpot(PlayerRecord record, int index, Spot spot) {
        record.spotList().set(index, spot);
        setDirty();
    }

    /** Swaps two spots in the player's list. The caller checks both indexes. */
    public void swapSpots(PlayerRecord record, int a, int b) {
        List<Spot> list = record.spotList();
        Spot first = list.get(a);
        list.set(a, list.get(b));
        list.set(b, first);
        setDirty();
    }

    /** Remembers where the player jumped from, for the "back" jump. */
    public void setBack(PlayerRecord record, Spot from) {
        record.setBack(from);
        setDirty();
    }

    // ---- saving and loading ----

    @Override
    public CompoundTag save(CompoundTag tag) {
        return write(tag);
    }

    private static JumperData load(CompoundTag tag) {
        JumperData data = new JumperData();
        data.read(tag);
        return data;
    }

    /**
     * Version of this file's layout (step 17). Files from before 1.0 have no number and the same layout as format 1.
     * Raise it when the layout changes, and convert older formats in {@link #read}.
     */
    public static final int FORMAT = 1;

    private CompoundTag write(CompoundTag tag) {
        tag.putInt("Format", FORMAT);
        ListTag anchorList = new ListTag();
        for (Map.Entry<AnchorKey, AnchorRecord> entry : anchors.entrySet()) {
            CompoundTag anchorTag = new CompoundTag();
            anchorTag.putString("Dim", entry.getKey().dimension());
            anchorTag.putLong("Pos", entry.getKey().pos());
            anchorTag.putInt("Tier", entry.getValue().tier());
            anchorTag.putLong("Energy", entry.getValue().energy());
            anchorList.add(anchorTag);
        }
        tag.put("Anchors", anchorList);

        ListTag playerList = new ListTag();
        for (Map.Entry<UUID, PlayerRecord> entry : players.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("Id", entry.getKey());
            AnchorKey link = entry.getValue().link();
            if (link != null) {
                playerTag.putString("LinkDim", link.dimension());
                playerTag.putLong("LinkPos", link.pos());
            }
            ListTag spotList = new ListTag();
            for (Spot spot : entry.getValue().spots()) {
                spotList.add(spot.write());
            }
            playerTag.put("Spots", spotList);
            if (entry.getValue().back() != null) {
                playerTag.put("Back", entry.getValue().back().write());
            }
            playerList.add(playerTag);
        }
        tag.put("Players", playerList);
        return tag;
    }

    private void read(CompoundTag tag) {
        int format = tag.getInt("Format");
        if (format > FORMAT) {
            // Saved by a newer Jumper. Read what this version understands; anything newer may be lost when it saves.
            Jumper.LOGGER.warn("Jumper's world data was saved by a newer version (format {}, this version reads {}). "
                    + "Update Jumper to keep all of it.", format, FORMAT);
        }
        ListTag anchorList = tag.getList("Anchors", Tag.TAG_COMPOUND);
        for (int i = 0; i < anchorList.size(); i++) {
            CompoundTag anchorTag = anchorList.getCompound(i);
            anchors.put(new AnchorKey(anchorTag.getString("Dim"), anchorTag.getLong("Pos")),
                    new AnchorRecord(anchorTag.getInt("Tier"), anchorTag.getLong("Energy")));
        }
        ListTag playerList = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < playerList.size(); i++) {
            CompoundTag playerTag = playerList.getCompound(i);
            if (!playerTag.hasUUID("Id")) {
                continue;
            }
            PlayerRecord record = new PlayerRecord();
            if (playerTag.contains("LinkDim", Tag.TAG_STRING)) {
                record.setLink(new AnchorKey(playerTag.getString("LinkDim"), playerTag.getLong("LinkPos")));
            }
            ListTag spotList = playerTag.getList("Spots", Tag.TAG_COMPOUND);
            for (int j = 0; j < spotList.size(); j++) {
                record.spotList().add(Spot.read(spotList.getCompound(j)));
            }
            if (playerTag.contains("Back", Tag.TAG_COMPOUND)) {
                record.setBack(Spot.read(playerTag.getCompound("Back")));
            }
            players.put(playerTag.getUUID("Id"), record);
        }
        recountAll();
    }
}
