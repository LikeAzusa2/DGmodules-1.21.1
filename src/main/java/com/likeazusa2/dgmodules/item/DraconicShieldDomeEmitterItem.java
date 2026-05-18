package com.likeazusa2.dgmodules.item;

import com.likeazusa2.dgmodules.entity.DomeEmitterProjectileEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class DraconicShieldDomeEmitterItem extends Item {

    public DraconicShieldDomeEmitterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENDER_PEARL_THROW, SoundSource.PLAYERS, 0.5F, 0.8F + level.getRandom().nextFloat() * 0.4F);

        if (!level.isClientSide) {
            DomeEmitterProjectileEntity projectile = new DomeEmitterProjectileEntity(level, player);
            projectile.setItem(stack);
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.3F, 0.5F);
            level.addFreshEntity(projectile);
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        stack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dgmodules.shield_dome.line1").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.dgmodules.shield_dome.line2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dgmodules.shield_dome.line3").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dgmodules.shield_dome.line4").withStyle(ChatFormatting.DARK_RED));
    }
}
