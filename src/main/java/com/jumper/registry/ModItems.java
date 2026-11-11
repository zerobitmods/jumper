package com.jumper.registry;

import com.jumper.Jumper;
import com.jumper.item.AnchorItem;
import com.jumper.item.AnchorUpgradeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Jumper.MOD_ID);

    public static final RegistryObject<AnchorItem> JUMPER_ANCHOR = ITEMS.register("jumper_anchor",
            () -> new AnchorItem(ModBlocks.JUMPER_ANCHOR.get(), new Item.Properties()));

    /** Upgrades: each raises an anchor exactly one tier (2, 3, 4), in order. */
    public static final RegistryObject<AnchorUpgradeItem> ANCHOR_UPGRADE_2 = ITEMS.register("anchor_upgrade_2",
            () -> new AnchorUpgradeItem(2, new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<AnchorUpgradeItem> ANCHOR_UPGRADE_3 = ITEMS.register("anchor_upgrade_3",
            () -> new AnchorUpgradeItem(3, new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<AnchorUpgradeItem> ANCHOR_UPGRADE_4 = ITEMS.register("anchor_upgrade_4",
            () -> new AnchorUpgradeItem(4, new Item.Properties().rarity(Rarity.EPIC)));

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
