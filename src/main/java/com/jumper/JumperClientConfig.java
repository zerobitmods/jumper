package com.jumper;

import com.jumper.client.JumpLayout;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The client config (config/jumper-client.toml): this player's own display choices, kept between sessions: the range
 * dome switch (set from the anchor's menu) and the jump screen's layout (set from its Layout button). Loaded only on a game client; a server never reads it.
 * Per version: only the config classes' names and how it registers differ on 1.20.1.
 */
public final class JumperClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue RANGE_DOME;
    /** The switch as last loaded or set, read every frame without asking the config library. */
    private static volatile boolean rangeDome;
    public static final int MIN_GRID = 1;
    public static final int MAX_GRID_COLUMNS = 12;
    public static final int MAX_GRID_ROWS = 8;
    private static final ModConfigSpec.EnumValue<JumpLayout> LAYOUT;
    private static final ModConfigSpec.IntValue GRID_COLUMNS;
    private static final ModConfigSpec.IntValue GRID_ROWS;
    private static volatile JumpLayout layout = JumpLayout.SQUARE;
    private static volatile int gridColumns = 4;
    private static volatile int gridRows = 4;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Show the range dome around your linked anchor. Turned on and off from the anchor's menu.");
        RANGE_DOME = builder.define("rangeDome", false);
        builder.comment("How the jump screen lays out your spots: SQUARE (eight around the power square), GRID (rows and",
                "columns, set below) or WHEEL (the ring). Changed from the jump screen's Layout button.");
        LAYOUT = builder.defineEnum("layout", JumpLayout.SQUARE);
        builder.comment("Columns of the GRID layout. Fewer are shown if the window is too small.");
        GRID_COLUMNS = builder.defineInRange("gridColumns", 4, MIN_GRID, MAX_GRID_COLUMNS);
        builder.comment("Rows of the GRID layout (columns x rows = spots per page). Fewer are shown if the window is too",
                "small.");
        GRID_ROWS = builder.defineInRange("gridRows", 4, MIN_GRID, MAX_GRID_ROWS);
        SPEC = builder.build();
    }

    private JumperClientConfig() {
    }

    public static void register(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SPEC);
        modEventBus.addListener(ModConfigEvent.Loading.class, event -> onFileChanged(event.getConfig()));
        modEventBus.addListener(ModConfigEvent.Reloading.class, event -> onFileChanged(event.getConfig()));
    }

    private static void onFileChanged(ModConfig config) {
        if (config.getSpec() == SPEC && SPEC.isLoaded()) {
            rangeDome = RANGE_DOME.get();
            layout = LAYOUT.get();
            gridColumns = GRID_COLUMNS.get();
            gridRows = GRID_ROWS.get();
        }
    }

    public static boolean rangeDome() {
        return rangeDome;
    }

    /** Turns the dome on or off and saves the file. */
    public static void setRangeDome(boolean on) {
        rangeDome = on;
        if (SPEC.isLoaded()) {
            RANGE_DOME.set(on);
            SPEC.save();
        }
    }

    public static JumpLayout layout() {
        return layout;
    }

    public static int gridColumns() {
        return gridColumns;
    }

    public static int gridRows() {
        return gridRows;
    }

    /** Sets the jump screen's layout and grid size (kept in range) and saves the file. */
    public static void setLayout(JumpLayout newLayout, int columns, int rows) {
        layout = newLayout;
        gridColumns = Math.max(MIN_GRID, Math.min(MAX_GRID_COLUMNS, columns));
        gridRows = Math.max(MIN_GRID, Math.min(MAX_GRID_ROWS, rows));
        if (SPEC.isLoaded()) {
            LAYOUT.set(layout);
            GRID_COLUMNS.set(gridColumns);
            GRID_ROWS.set(gridRows);
            SPEC.save();
        }
    }
}
