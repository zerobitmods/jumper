package com.jumper.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;

/**
 * The loader-specific part of drawing a fluid picture: its still texture, tinted the way the fluid's mod says (water is
 * blue this way). Kept apart so {@link Picture} stays the same file on 1.20.1 and 1.21.1. Client only.
 */
final class FluidPictures {
    private FluidPictures() {
    }

    /** Draws the fluid as a 16 x 16 square. False if it has no texture or drawing failed (draw something else). */
    static boolean draw(GuiGraphics graphics, Fluid fluid, int x, int y) {
        try {
            IClientFluidTypeExtensions looks = IClientFluidTypeExtensions.of(fluid);
            ResourceLocation still = looks.getStillTexture();
            if (still == null) {
                return false;
            }
            TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(still);
            int tint = looks.getTintColor();
            float alpha = (tint >>> 24) / 255.0F;
            graphics.blit(x, y, 0, 16, 16, sprite, ((tint >> 16) & 0xFF) / 255.0F, ((tint >> 8) & 0xFF) / 255.0F,
                    (tint & 0xFF) / 255.0F, alpha == 0.0F ? 1.0F : alpha);
            return true;
        } catch (RuntimeException problem) {
            return false;
        }
    }

    /** The fluid's name as its mod gives it ("Water", "Molten Iron"). */
    static Component name(Fluid fluid) {
        return fluid.getFluidType().getDescription();
    }
}
