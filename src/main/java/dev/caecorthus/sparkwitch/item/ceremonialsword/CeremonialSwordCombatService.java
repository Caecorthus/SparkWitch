package dev.caecorthus.sparkwitch.item.ceremonialsword;

import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchRules;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchRuntimeComponent;
import net.minecraft.item.ItemStack;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;

import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.api.event.KillPlayer;
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
        KillPlayer.AFTER.register((victim, killer, reason) -> {
            if (killer != null && SparkWitchDeathReasons.CEREMONIAL_BLADE.equals(reason)
                    && GrandWitchRuntimeComponent.KEY.get(killer).isSwordStrikeInProgress()) {
                completeSwordKill(killer, victim);
            }
        });
    }

    public static ActionResult tryHandleAttack(Entity attacker, World world, Hand hand, Entity target) {
        boolean holdingCeremonialSword = attacker instanceof PlayerEntity player
                && player.getStackInHand(hand).isOf(SparkWitchItems.ceremonialSword());
        if (!holdingCeremonialSword) {
            return ActionResult.PASS;
        }
        if (attacker instanceof PlayerEntity player
                && SparkTraitsKillerBridge.blocksWeaponAction(player, player.getStackInHand(hand))) {
            return ActionResult.FAIL;
        }
        if (world.isClient) {
            if (attacker instanceof PlayerEntity player && player.getAttackCooldownProgress(0.0f) >= 1.0f) {
                player.resetLastAttackedTicks();
            }
            return clientAttackResult(true, target instanceof PlayerEntity);
        }
        if (!(attacker instanceof ServerPlayerEntity serverAttacker)) {
            return ActionResult.PASS;
        }
        AttackDecision decision = decideAttack(
                canStrike(serverAttacker, target),
                serverAttacker.getAttackCooldownProgress(0.0f) >= 1.0f
        );

        // Sword attacks never fall through to vanilla damage, including cooldown/invalid targets.
        // 仪礼剑攻击不会回退原版伤害，包括冷却中攻击和无效目标。
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
        // SUCCESS sends the packet; only the server decides whether vanilla charge is ready.
        // SUCCESS 发送攻击包；仅服务端决定原版蓄力是否完成。
        return holdingCeremonialSword ? ActionResult.SUCCESS : ActionResult.PASS;
    }

    public static AttackDecision decideAttack(boolean canStrike, boolean fullyCooledAttack) {
        return new AttackDecision(true, fullyCooledAttack, canStrike && fullyCooledAttack);
    }

    public static boolean canStrike(ServerPlayerEntity attacker, Entity target) {
        if (!(target instanceof ServerPlayerEntity serverTarget)) {
            return false;
        }
        return canStrikeTarget(
                attacker.getUuid().equals(serverTarget.getUuid()),
                VendettaInteractionService.isOrdinaryAliveOrBoundKillerTarget(attacker, serverTarget),
                GameFunctions.isPlayerAliveAndSurvival(serverTarget)
        );
    }

    /**
     * Ceremonial sword kills are item-bound: the holder may be outside the round or creative,
     * but the victim must be an ordinary in-round target or the holder's exact bound Vendetta.
     * 仪礼剑击杀只绑定物品：持有者可以不在局内或处于创造，但目标必须是局内玩家或其精确绑定的仇杀客。
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

    /**
     * Stable void entry point injected by SparkFactionAPI; direct and dash contacts share this gate.
     * SparkFactionAPI 注入的稳定 void 入口；左键与冲刺接触共用此门槛。
     */
    public static void killWithCeremonialSword(ServerPlayerEntity attacker, ServerPlayerEntity target) {
        GrandWitchRuntimeComponent runtime = GrandWitchRuntimeComponent.KEY.get(attacker);
        if (!GrandWitchRules.canAttemptCeremonialSwordKill(
                runtime.getSwordKillCooldownTicks(), runtime.isSwordStrikeInProgress())) {
            return;
        }
        // Both direct strikes and delayed dash contacts are immediate melee, never the dash skill cooldown.
        // 左键与延迟冲刺接触都是即时近战，不能把冲刺技能冷却当成左键禁用。
        ItemStack weapon = new ItemStack(SparkWitchItems.ceremonialSword());
        if (!canStrike(attacker, target)
                || SparkTraitsKillerBridge.blocksWeaponAction(attacker, weapon)) {
            return;
        }
        runtime.setSwordStrikeInProgress(true);
        try {
            if (SparkTraitsKillerBridge.shouldCancelMeleeAttack(attacker, target, weapon)) {
                return;
            }
            GameRecordManager.recordItemUse(
                    attacker,
                    Registries.ITEM.getId(SparkWitchItems.ceremonialSword()),
                    target,
                    null
            );
            boolean boundVendetta = VendettaInteractionService.isActiveVendetta(target)
                    && VendettaInteractionService.isExactPair(attacker, target);
            GameFunctions.killPlayer(target, true, attacker, SparkWitchDeathReasons.CEREMONIAL_BLADE);
            // Vendetta terminal removal has no ordinary AFTER event; its cleared bond confirms that outcome.
            // 仇杀客的终止移除不派发普通 AFTER；其他旁观切换不能作为确认击杀的依据。
            if (boundVendetta && !VendettaInteractionService.isActiveVendetta(target) && target.isSpectator()) {
                completeSwordKill(attacker, target);
            }
        } finally {
            runtime.setSwordStrikeInProgress(false);
        }
    }

    private static void completeSwordKill(ServerPlayerEntity attacker, ServerPlayerEntity target) {
        GrandWitchRuntimeComponent.KEY.get(attacker)
                .setSwordKillCooldownTicks(GrandWitchRules.CEREMONIAL_SWORD_KILL_COOLDOWN_TICKS);
        target.playSound(SoundEvents.ITEM_TRIDENT_HIT, 1.0f, 0.8f);
    }

    public record AttackDecision(boolean handled, boolean resetVanillaCooldown, boolean kill) {
    }
}
