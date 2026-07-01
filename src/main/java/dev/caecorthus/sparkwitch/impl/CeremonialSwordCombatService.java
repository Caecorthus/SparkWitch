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
        if (!canStrike(serverAttacker, target)) {
            return ActionResult.PASS;
        }

        // Non-PASS keeps vanilla damage from bypassing the ceremonial sword's custom cooldown gate.
        // 返回非 PASS，避免原版伤害绕过仪礼剑自定义击杀的攻击冷却门槛。
        if (!hasFullyCooledAttack(serverAttacker)) {
            return ActionResult.SUCCESS;
        }

        killWithCeremonialSword(serverAttacker, (ServerPlayerEntity) target);
        serverAttacker.resetLastAttackedTicks();
        return ActionResult.SUCCESS;
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
}
