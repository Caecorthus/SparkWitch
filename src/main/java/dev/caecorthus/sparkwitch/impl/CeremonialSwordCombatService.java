package dev.caecorthus.sparkwitch.impl;

import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

public final class CeremonialSwordCombatService {
    private static boolean registered;

    private CeremonialSwordCombatService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                tryHandleAttack(player, world, hand, entity));
    }

    public static ActionResult tryHandleAttack(Entity attacker, World world, Hand hand, Entity target) {
        if (world.isClient
                || !(attacker instanceof ServerPlayerEntity serverAttacker)
                || !serverAttacker.getStackInHand(hand).isOf(SparkWitchItems.ceremonialSword())) {
            return ActionResult.PASS;
        }
        AttackDecision decision = decideAttack(canStrike(serverAttacker, target), hasFullyCooledAttack(serverAttacker));
        if (!decision.handled()) {
            return ActionResult.PASS;
        }

        // SUCCESS keeps vanilla damage from bypassing the custom kill gate; the reset mirrors vanilla attack timing.
        // 返回 SUCCESS 防止原版伤害绕过自定义击杀门槛；重置攻击间隔则沿用原版左键冷却时序。
        if (decision.resetVanillaCooldown()) {
            serverAttacker.resetLastAttackedTicks();
        }
        if (!decision.kill()) {
            return ActionResult.SUCCESS;
        }

        if (target instanceof ServerPlayerEntity serverTarget) {
            killWithCeremonialSword(serverAttacker, serverTarget);
        }
        return ActionResult.SUCCESS;
    }

    public static AttackDecision decideAttack(boolean canStrike, boolean fullyCooledAttack) {
        if (!canStrike) {
            return new AttackDecision(false, false, false);
        }
        return new AttackDecision(true, true, fullyCooledAttack);
    }

    public static boolean hasFullyCooledAttack(ServerPlayerEntity attacker) {
        return attacker.getAttackCooldownProgress(0.5f) >= 1.0f;
    }

    public static boolean canStrike(ServerPlayerEntity attacker, Entity target) {
        if (!GameFunctions.isPlayerPlayingAndAlive(attacker)
                || !(target instanceof ServerPlayerEntity serverTarget)
                || attacker.getUuid().equals(serverTarget.getUuid())) {
            return false;
        }
        return GameFunctions.isPlayerPlayingAndAlive(serverTarget)
                && GameFunctions.isPlayerAliveAndSurvival(serverTarget);
    }

    public static void killWithCeremonialSword(ServerPlayerEntity attacker, ServerPlayerEntity target) {
        GameRecordManager.recordItemUse(
                attacker,
                Registries.ITEM.getId(SparkWitchItems.ceremonialSword()),
                target,
                null
        );
        GameFunctions.killPlayer(target, true, attacker, SparkWitchDeathReasons.CEREMONIAL_BLADE);
        target.playSound(SoundEvents.ITEM_TRIDENT_HIT, 1.0f, 0.8f);
    }

    public record AttackDecision(boolean handled, boolean resetVanillaCooldown, boolean kill) {
    }
}
