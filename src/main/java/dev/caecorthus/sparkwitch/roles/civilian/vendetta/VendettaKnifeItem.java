package dev.caecorthus.sparkwitch.roles.civilian.vendetta;

import dev.doctor4t.wathe.item.KnifeItem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.List;

/** Wathe-style hold-and-release knife whose payload can name only the owner's bound killer. */
public final class VendettaKnifeItem extends KnifeItem {
    public VendettaKnifeItem(Settings settings) {
        super(settings);
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        int heldTicks = getMaxUseTime(stack, user) - remainingUseTicks;
        if (!world.isClient && user instanceof ServerPlayerEntity serverPlayer) {
            VendettaKnifeService.recordServerRelease(serverPlayer, heldTicks);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.sparkwitch.vendetta_knife.desc").formatted(Formatting.GRAY));
    }
}
