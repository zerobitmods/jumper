package com.jumper.block;

import com.jumper.JumperRules;
import com.jumper.data.AnchorKey;
import com.jumper.data.JumperData;
import com.jumper.item.AnchorItem;
import com.jumper.item.AnchorUpgradeItem;
import com.jumper.jump.AnchorMenu;
import com.jumper.jump.JumpService;
import com.jumper.jump.JumperSync;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import com.jumper.registry.ModBlockEntities;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Jumper Anchor: the block a base's jumps are measured from. Every anchor is listed in {@link JumperData}, so a
 * jump can be checked without loading the anchor's chunk. Its block entity ({@link AnchorBlockEntity}) takes in power
 * and pays the upkeep once a second while the chunk is running.
 * <p>
 * Its tier survives being broken: the dropped item carries it ({@link #getDrops}) and placing that item sets it again
 * ({@link #setPlacedBy}). Stored power is lost.
 */
public class JumperAnchorBlock extends Block implements EntityBlock {

    /**
     * The tier the block shows (lit lights and glow colour; one model per tier). Only a mirror for the look: the real
     * tier lives in {@link JumperData}, and {@link #showTier} plus the block entity's once-a-second check keep this in
     * step with it.
     */
    public static final IntegerProperty TIER = IntegerProperty.create("tier", JumperRules.MIN_TIER, JumperRules.MAX_TIER);

    public JumperAnchorBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(TIER, JumperRules.MIN_TIER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TIER);
    }

    /** Placed from an item that remembers a tier: show that tier from the first frame. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(TIER, JumperRules.clampTier(AnchorItem.tierOf(context.getItemInHand())));
    }

    /**
     * Makes the anchor block at pos show this tier. Never loads a chunk; does nothing if the block there isn't an anchor
     * or already shows it. Only clients are told (no neighbour updates).
     */
    public static void showTier(Level level, BlockPos pos, int tier) {
        if (!level.isLoaded(pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        int shown = JumperRules.clampTier(tier);
        if (state.getBlock() instanceof JumperAnchorBlock && state.getValue(TIER) != shown) {
            level.setBlock(pos, state.setValue(TIER, shown), Block.UPDATE_CLIENTS);
        }
    }

    /** The same for a listed anchor, wherever it is (skipped if its dimension or chunk isn't loaded). */
    public static void showTier(MinecraftServer server, AnchorKey key, int tier) {
        ServerLevel level = JumpService.levelOf(server, key.dimension());
        if (level != null) {
            showTier(level, key.blockPos(), tier);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AnchorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide ? null : ticker(type, ModBlockEntities.JUMPER_ANCHOR.get(), AnchorBlockEntity::serverTick);
    }

    @SuppressWarnings("unchecked")
    private static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> ticker(BlockEntityType<A> given,
            BlockEntityType<E> expected, BlockEntityTicker<? super E> ticker) {
        return expected == given ? (BlockEntityTicker<A>) ticker : null;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        MinecraftServer server = level.getServer();
        if (!level.isClientSide && server != null && !oldState.is(state.getBlock())) {
            // The tier from the item, if setPlacedBy already ran (it does first when a player places it).
            int tier = level.getBlockEntity(pos) instanceof AnchorBlockEntity anchorEntity
                    ? anchorEntity.placedTier() : JumperRules.MIN_TIER;
            JumperData.get(server).placeAnchor(level, pos, tier);
            // Players still linked to this spot (the anchor was broken and put back) get their wheel back right away.
            JumperSync.sendToLinked(server, JumperData.key(level, pos));
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        MinecraftServer server = level.getServer();
        if (!level.isClientSide && server != null && !state.is(newState.getBlock())) {
            JumperData.get(server).removeAnchor(level, pos);
            // Players linked to this anchor see their wheel go empty right away.
            JumperSync.sendToLinked(server, JumperData.key(level, pos));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Placed from an item that remembers a tier (an anchor that was upgraded, then broken): give it that tier. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        MinecraftServer server = level.getServer();
        int tier = AnchorItem.tierOf(stack);
        if (level.isClientSide || server == null || tier <= JumperRules.MIN_TIER) {
            return;
        }
        // When a player places it, the loader runs onPlace (which lists the anchor) only after this, so the tier is
        // left on the block entity for onPlace to use. Otherwise onPlace already ran and the tier is set here.
        if (level.getBlockEntity(pos) instanceof AnchorBlockEntity anchorEntity) {
            anchorEntity.setPlacedTier(tier);
        }
        AnchorKey key = JumperData.key(level, pos);
        JumperData.get(server).setTier(key, tier);
        JumperSync.sendToLinked(server, key);
    }

    /**
     * Mined by a player: the block (and its entry in {@link JumperData}) is removed before the drops are worked out, so
     * the block entity notes the tier now.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof AnchorBlockEntity anchorEntity) {
            anchorEntity.rememberTier();
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Whatever the loot table drops: any anchor item in it gets this anchor's tier. */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof AnchorBlockEntity anchorEntity) {
            int tier = anchorEntity.dropTier();
            if (tier > JumperRules.MIN_TIER) {
                for (ItemStack drop : drops) {
                    if (drop.getItem() instanceof AnchorItem) {
                        AnchorItem.setTier(drop, tier);
                    }
                }
            }
        }
        return drops;
    }

    /**
     * Holding an upgrade: skip the menu so the click goes on to the upgrade item ({@link AnchorUpgradeItem#useOn}).
     * Anything else (or an empty hand) opens the menu as usual.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.getItem() instanceof AnchorUpgradeItem) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    /**
     * Right-click: open the anchor's menu (its tier and power, the range dome switch, and a Link button if the player
     * isn't linked here). The server sends the menu's numbers; linking happens only from its button.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            AnchorMenu.open(serverPlayer, level, pos);
        }
        return InteractionResult.CONSUME;
    }
}
