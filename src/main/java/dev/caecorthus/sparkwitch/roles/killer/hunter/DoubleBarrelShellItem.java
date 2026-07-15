package dev.caecorthus.sparkwitch.roles.killer.hunter;

import dev.caecorthus.sparkwitch.SparkWitch;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public final class DoubleBarrelShellItem extends Item {
    public static final Identifier ID = SparkWitch.id("double_barrel_shell");

    public DoubleBarrelShellItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Settings().maxCount(16);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack shells = user.getStackInHand(hand);
        ItemStack shotgun = user.getStackInHand(hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND);
        if (!(shotgun.getItem() instanceof DoubleBarrelShotgunItem)) {
            return TypedActionResult.pass(shells);
        }
        if (!DoubleBarrelShotgunItem.tryReload(user, shotgun, shells)) {
            return TypedActionResult.fail(shells);
        }
        return TypedActionResult.success(shells, world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.sparkwitch.double_barrel_shell.tooltip"));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
