package com.jumper.client;

/**
 * How the jump screen lays out the spots (the player's choice, kept in the client config). The same file on 1.20.1 and
 * 1.21.1.
 */
public enum JumpLayout {
    /** Eight squares around a middle square that shows the power (the default since step 25). */
    SQUARE,
    /** A plain grid of squares, as many columns and rows as the player picks; names show in the hint line. */
    GRID,
    /** The ring of wedges the mod started with. */
    WHEEL
}
