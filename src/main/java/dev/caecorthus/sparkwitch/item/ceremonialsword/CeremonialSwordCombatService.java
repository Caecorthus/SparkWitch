package dev.caecorthus.sparkwitch.item.ceremonialsword;

import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
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
        boolean holdingCeremonialSword = attacker instanceof PlayerEntity player
                && player.getStackInHand(hand).isOf(SparkWitchItems.ceremonialSword());
        if (!holdingCeremonialSword) {
            return ActionResult.PASS;
        }
        if (world.isClient) {
            return clientAttackResult(true, target instanceof PlayerEntity);
        }
        if (!(attacker instanceof ServerPlayerEntity serverAttacker)) {
            return ActionResult.PASS;
        }
        AttackDecision decision = decideAttack(canStrike(serverAttacker, target), true);
        if (!decision.handled()) {
            return ActionResult.PASS;
        }

        // SUCCESS keeps vanilla damage from bypassing the custom kill gate without adding vanilla cooldown.
        // 返回 SUCCESS 防止原版伤害绕过自定义击杀门槛，同时不附加原版左键冷却。
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

    public static ActionResult clientAttackResult(boolean holdingCeremonialSword, boolean targetPlayer) {
        // SUCCESS keeps Fabric sending the attack packet while skipping client-side vanilla cooldown prediction.
        // 返回 SUCCESS 继续让 Fabric 发送攻击包，同时跳过客户端原版攻击冷却预测。
        return holdingCeremonialSword && targetPlayer ? ActionResult.SUCCESS : ActionResult.PASS;
    }

    public static AttackDecision decideAttack(boolean canStrike, boolean fullyCooledAttack) {
        if (!canStrike) {
            return new AttackDecision(false, false, false);
        }
        return new AttackDecision(true, false, true);
    }

    public static boolean canStrike(ServerPlayerEntity attacker, Entity target) {
        if (!(target instanceof ServerPlayerEntity serverTarget)) {
            return false;
        }
        return canStrikeTarget(
                attacker.getUuid().equals(serverTarget.getUuid()),
                GameFunctions.isPlayerPlayingAndAlive(serverTarget),
                GameFunctions.isPlayerAliveAndSurvival(serverTarget)
        );
    }

    /**
     * Ceremonial sword kills are item-bound: the holder may be outside the round or creative,
     * but the victim must still be an in-round survival player.
     * 仪礼剑击杀只绑定物品：持有者可以不在局内或处于创造，但目标必须仍是局内生存玩家。
     */
    public static boolean canStrikeTarget(
            boolean samePlayer,
            boolean targetPlayingAndAlive,
            boolean targetAliveAndSurvival
    ) {
        return !samePlayer
                && targetPlayingAndAlive
                && targetAliveAndSurvival;
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
