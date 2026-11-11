package com.jumper.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The one part of the wheel that differs between Minecraft versions: how the game hands over a mouse scroll. Everything
 * else lives in {@link WheelScreen}, which is the same file on 1.20.1 and 1.21.1.
 */
public abstract class WheelScreenBase extends Screen {

    protected WheelScreenBase(Component title) {
        super(title);
    }

    /**
     * The mouse wheel moved.
     *
     * @param amount above 0 = scrolled up, below 0 = scrolled down
     * @return true if the wheel used it
     */
    protected abstract boolean onScroll(double amount);

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // On a Mac, holding Shift turns an up/down scroll into a sideways one; treat both the same.
        double amount = scrollY != 0.0 ? scrollY : scrollX;
        return onScroll(amount) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
