/*
 * Derived from StarRailExpress NinjaShurikenItem at commit
 * 220d03ede335fc7971fcffbc302bc68bb91b0209 (GPL-3.0-only).
 * SparkWitch adaptations are AGPL-3.0-only; see THIRD_PARTY_NOTICES.md.
 */
package dev.caecorthus.sparkwitch.item.ninja;

import dev.caecorthus.sparkwitch.entity.NinjaShurikenEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

import java.util.List;

public final class NinjaShurikenItem extends Item {
    public static final int MIN_CHARGE_TICKS = 4;
    public static final int SHURIKEN_COOLDOWN_TICKS = 20;
    public static final float PROJECTILE_SPEED = 1.3F;
    public static final float PROJECTILE_INACCURACY = 1.0F;
    private static final int MAX_USE_TICKS = 7200;

    public NinjaShurikenItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.isSpectator() || user.getItemCooldownManager().isCoolingDown(this)) {
            return TypedActionResult.pass(stack);
        }
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (world.isClient()
                || !(user instanceof ServerPlayerEntity thrower)
                || !GameFunctions.isPlayerPlayingAndAlive(thrower)
                || !GameFunctions.isPlayerAliveAndSurvival(thrower)
                || thrower.getItemCooldownManager().isCoolingDown(this)
                || getMaxUseTime(stack, user) - remainingUseTicks < MIN_CHARGE_TICKS) {
            return;
        }

        // Vanilla release handling reaches the server directly, so no custom throw packet is needed.
        // 原版松手流程会直接抵达服务端，因此无需自定义投掷网络包。
        NinjaShurikenEntity projectile = new NinjaShurikenEntity(world, thrower, stack);
        projectile.setVelocity(
                thrower,
                thrower.getPitch(),
                thrower.getYaw(),
                0.0F,
                PROJECTILE_SPEED,
                PROJECTILE_INACCURACY
        );
        if (world.spawnEntity(projectile)) {
            stack.decrement(1);
            thrower.getItemCooldownManager().set(this, SHURIKEN_COOLDOWN_TICKS);
        }
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.SPEAR;
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return MAX_USE_TICKS;
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return false;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.sparkwitch.ninja_shuriken.desc").formatted(Formatting.GRAY));
    }
}
