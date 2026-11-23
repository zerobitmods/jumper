package com.jumper.client;

import com.jumper.JumperRules;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * A spot's picture as the client draws it: an item, or a fluid. The server stores it as a text id: an item id like
 * "minecraft:furnace", or a fluid id after {@link JumperRules#FLUID_PICTURE} ("fluid/minecraft:water"). Anything this
 * client can't show becomes an ender pearl. Client only; the same file on 1.20.1 and 1.21.1.
 */
public final class Picture {
    private static final ItemStack FALLBACK = new ItemStack(Items.ENDER_PEARL);

    private final ItemStack item;
    /** Set for a fluid picture (then item is the fallback drawn if the fluid can't be). */
    private final Fluid fluid;

    private Picture(ItemStack item, Fluid fluid) {
        this.item = item;
        this.fluid = fluid;
    }

    /** The picture for an id from the server; an ender pearl if the id is empty or unknown on this client. */
    public static Picture of(String id) {
        if (id.startsWith(JumperRules.FLUID_PICTURE)) {
            Fluid fluid = fluidFor(id.substring(JumperRules.FLUID_PICTURE.length()));
            return fluid == null ? of(FALLBACK) : of(fluid);
        }
        ResourceLocation key = id.isEmpty() ? null : ResourceLocation.tryParse(id);
        Item item = key == null ? Items.AIR : BuiltInRegistries.ITEM.get(key);
        return of(new ItemStack(item == Items.AIR ? Items.ENDER_PEARL : item));
    }

    public static Picture of(ItemStack stack) {
        return new Picture(stack.isEmpty() ? FALLBACK.copy() : stack.copyWithCount(1), null);
    }

    public static Picture of(Fluid fluid) {
        return new Picture(FALLBACK.copy(), fluid);
    }

    /** The fluid with this id, or null if there is none (or it's the empty fluid). */
    private static Fluid fluidFor(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.FLUID.containsKey(key)) {
            return null;
        }
        Fluid fluid = BuiltInRegistries.FLUID.get(key);
        return fluid == Fluids.EMPTY ? null : fluid;
    }

    /** The id the server stores for this picture. */
    public String id() {
        if (this.fluid != null) {
            return JumperRules.FLUID_PICTURE + BuiltInRegistries.FLUID.getKey(this.fluid);
        }
        return BuiltInRegistries.ITEM.getKey(this.item.getItem()).toString();
    }

    /** What the picture shows, for suggested names and hints. */
    public Component name() {
        if (this.fluid != null) {
            try {
                return FluidPictures.name(this.fluid);
            } catch (RuntimeException problem) {
                return Component.literal(BuiltInRegistries.FLUID.getKey(this.fluid).getPath());
            }
        }
        return this.item.getHoverName();
    }

    /** Draws the picture as a 16 x 16 square with its top-left corner here. */
    public void draw(GuiGraphics graphics, int x, int y) {
        if (this.fluid != null && FluidPictures.draw(graphics, this.fluid, x, y)) {
            return;
        }
        graphics.renderItem(this.item, x, y);
    }
}
