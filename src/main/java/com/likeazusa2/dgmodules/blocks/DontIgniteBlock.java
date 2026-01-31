package com.likeazusa2.dgmodules.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * "Don't Ignite" easter-egg block.
 *
 * Notes for 1.21+:
 * - Block interaction is split into useItemOn / useWithoutItem.
 * - broadcastBreakEvent now takes an EquipmentSlot (not InteractionHand).
 */
public class DontIgniteBlock extends Block implements EntityBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public DontIgniteBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    private static boolean isIgnitionItem(ItemStack stack) {
        return stack.getItem() instanceof FlintAndSteelItem || stack.getItem() instanceof FireChargeItem;
    }

    private static EquipmentSlot slotFromHand(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
    }

    private void ignite(Level level, BlockPos pos, @Nullable Player player) {
        BlockState state = level.getBlockState(pos);
        if (state.getValue(LIT)) return;

        level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL);

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof DontIgniteBlockEntity egg) {
            egg.start(player);
        }
    }

    /**
     * Called when a player uses an item on this block.
     */
    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!isIgnitionItem(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide) {
            ignite(level, pos, player);

            // Consume durability / item just like vanilla.
            if (stack.getItem() instanceof FlintAndSteelItem) {
                EquipmentSlot slot = (hand == InteractionHand.MAIN_HAND) ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
                stack.hurtAndBreak(1, player, slot);
            } else if (stack.getItem() instanceof FireChargeItem) {
                stack.shrink(1);
            }
        }

        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Called when a player interacts with an empty hand (or no item-handled interaction).
     * We do nothing special here.
     */
    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    /**
     * NeoForge fire hook. Signature differs across versions/mappings; keep it non-@Override-safe.
     */
    public void onCaughtFire(BlockState state, Level level, BlockPos pos, @Nullable Direction face, @Nullable LivingEntity igniter) {
        if (!level.isClientSide) {
            Player player = igniter instanceof Player p ? p : null;
            ignite(level, pos, player);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DontIgniteBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return (lvl, p, s, be) -> {
            if (be instanceof DontIgniteBlockEntity egg) {
                egg.serverTick((ServerLevel) lvl, p, s);
            }
        };
    }
}
