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

public final class RitualSwordCombatService {
    private static boolean registered;

    private RitualSwordCombatService() {
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
        // Non-PASS cancels vanilla attack so ritual sword strikes do not inherit attack cooldown.
        // 返回非 PASS 会取消原版攻击，让仪式剑左键不继承攻击冷却。
        if (world.isClient
                || !(attacker instanceof ServerPlayerEntity serverAttacker)
                || !serverAttacker.getStackInHand(hand).isOf(SparkWitchItems.ritualSword())
                || !canStrike(serverAttacker, target)) {
            return ActionResult.PASS;
        }

        killWithRitualSword(serverAttacker, (ServerPlayerEntity) target);
        return ActionResult.SUCCESS;
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

    public static void killWithRitualSword(ServerPlayerEntity attacker, ServerPlayerEntity target) {
        GameRecordManager.recordItemUse(
                attacker,
                Registries.ITEM.getId(SparkWitchItems.ritualSword()),
                target,
                null
        );
        GameFunctions.killPlayer(target, true, attacker, SparkWitchDeathReasons.RITUAL_BLADE);
        target.playSound(SoundEvents.ITEM_TRIDENT_HIT, 1.0f, 0.8f);
    }
}
