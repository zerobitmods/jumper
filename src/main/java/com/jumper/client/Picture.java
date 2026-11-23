package com.jumper.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A spot's picture as the client draws it. The server stores it as a text id (an item id like "minecraft:furnace");
 * anything this client can't show becomes an ender pearl. Client only.
 */
public final class Picture {
    private final ItemStack item;

    private Picture(ItemStack item) {
        this.item = item;
    }

    /** The picture for an id from the server; an ender pearl if the id is empty or unknown on this client. */
    public static Picture of(String id) {
        ResourceLocation key = id.isEmpty() ? null : ResourceLocation.tryParse(id);
        Item item = key == null ? Items.AIR : BuiltInRegistries.ITEM.get(key);
        return of(new ItemStack(item == Items.AIR ? Items.ENDER_PEARL : item));
    }

    public static Picture of(ItemStack stack) {
        return new Picture(stack.isEmpty() ? new ItemStack(Items.ENDER_PEARL) : stack.copyWithCount(1));
    }

    /** The id the server stores for this picture. */
    public String id() {
        return BuiltInRegistries.ITEM.getKey(this.item.getItem()).toString();
    }

    /** What the picture shows, for suggested names and hints. */
    public Component name() {
        return this.item.getHoverName();
    }

    /** Draws the picture as a 16 x 16 square with its top-left corner here. */
    public void draw(GuiGraphics graphics, int x, int y) {
        graphics.renderItem(this.item, x, y);
    }
}
