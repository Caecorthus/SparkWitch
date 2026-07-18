/*
 * Derived from StarRailExpress NinjaKnifeItem at commit
 * 220d03ede335fc7971fcffbc302bc68bb91b0209 (GPL-3.0-only).
 * SparkWitch adaptations are AGPL-3.0-only; see THIRD_PARTY_NOTICES.md.
 */
package dev.caecorthus.sparkwitch.item.ninja;

import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

import java.util.List;

public final class NinjaKnifeItem extends Item {
    public static final double MAX_RANGE = 4.0;
    public static final int KNIFE_COOLDOWN_TICKS = 30 * 20;

    public NinjaKnifeItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, net.minecraft.entity.player.PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient()
                || !(user instanceof ServerPlayerEntity attacker)
                || !GameFunctions.isPlayerPlayingAndAlive(attacker)
                || !GameFunctions.isPlayerAliveAndSurvival(attacker)
                || attacker.getItemCooldownManager().isCoolingDown(this)) {
            return TypedActionResult.pass(stack);
        }

        // Targeting and death stay server-authoritative; item ownership is intentionally role-agnostic.
        // 瞄准与死亡仅由服务端裁定；物品持有者不受角色限制。
        HitResult collision = ProjectileUtil.getCollision(
                attacker,
                entity -> entity instanceof ServerPlayerEntity victim
                        && victim != attacker
                        && VendettaInteractionService.isOrdinaryAliveOrBoundKillerTarget(attacker, victim)
                        && GameFunctions.isPlayerAliveAndSurvival(victim),
                MAX_RANGE
        );
        if (!(collision instanceof EntityHitResult entityHit)
                || !(entityHit.getEntity() instanceof ServerPlayerEntity victim)) {
            return TypedActionResult.pass(stack);
        }

        GameFunctions.killPlayer(victim, true, attacker, SparkWitchDeathReasons.NINJA_KNIFE_KILL);
        attacker.getItemCooldownManager().set(this, KNIFE_COOLDOWN_TICKS);
        return TypedActionResult.consume(stack);
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return false;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.sparkwitch.ninja_knife.desc").formatted(Formatting.GRAY));
    }
}
