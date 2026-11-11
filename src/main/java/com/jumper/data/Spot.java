package com.jumper.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * A saved place: where to stand and which way to face, plus the item shown for it on the wheel (an item id like
 * "minecraft:furnace", or "" for none). Never changes once made; renaming or changing the picture makes a new one.
 */
public record Spot(String name, String icon, String dimension, double x, double y, double z, float yaw, float pitch) {

    /** A spot at the player's feet, facing the way they face now. */
    public static Spot at(String name, String icon, ServerPlayer player) {
        return new Spot(name, icon, JumperData.dimensionId(player.level()), player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
    }

    public Spot withName(String newName) {
        return new Spot(newName, icon, dimension, x, y, z, yaw, pitch);
    }

    public Spot withIcon(String newIcon) {
        return new Spot(name, newIcon, dimension, x, y, z, yaw, pitch);
    }

    CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        tag.putString("Icon", icon);
        tag.putString("Dim", dimension);
        tag.putDouble("X", x);
        tag.putDouble("Y", y);
        tag.putDouble("Z", z);
        tag.putFloat("Yaw", yaw);
        tag.putFloat("Pitch", pitch);
        return tag;
    }

    static Spot read(CompoundTag tag) {
        return new Spot(tag.getString("Name"), tag.getString("Icon"), tag.getString("Dim"), tag.getDouble("X"), tag.getDouble("Y"),
                tag.getDouble("Z"), tag.getFloat("Yaw"), tag.getFloat("Pitch"));
    }
}
