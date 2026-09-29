package dev.caecorthus.sparkwitch.roles.civilian.fisher.swordfish;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.FisherRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceRaycast;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteSessionService;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server validation of a Swordfish stab, consumption and friendly-fire death. / 剑鱼刺杀校验、消耗与小脑。 */
public final class SwordfishStabService {
    private static final Map<UUID, QualifiedRelease> RELEASES = new HashMap<>();
    private static boolean registered;

    private SwordfishStabService() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        ResetPlayer.EVENT.register(SwordfishStabService::clearPlayer);
        RoleAssigned.EVENT.register((player, role) -> RELEASES.remove(player.getUuid()));
        KillPlayer.AFTER.register((victim, killer, reason) -> clearPlayer(victim));
        GameEvents.ON_GAME_START.register(mode -> RELEASES.clear());
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> RELEASES.clear());
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clearPlayer(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> RELEASES.clear());
        ServerTickEvents.END_SERVER_TICK.register(server -> RELEASES.values().removeIf(release ->
                !SwordfishRules.qualifiedRelease(release.heldTicks(),
                        release.world().getTime() - release.releasedAt(), true, true)));
    }

    /** Called from the server release of {@link SwordfishItem}. / 由剑鱼的服务端松手调用。 */
    public static void recordServerRelease(ServerPlayerEntity player, int heldTicks) {
        if (player == null) {
            return;
        }
        clearPlayer(player);
        ItemStack stack = player.getMainHandStack();
        if (!SwordfishRules.qualifiedHold(heldTicks) || !player.isUsingItem()
                || player.getActiveHand() != Hand.MAIN_HAND || player.getActiveItem() != stack
                || !canUse(player, stack)) {
            return;
        }
        RELEASES.put(player.getUuid(), new QualifiedRelease(heldTicks, player.getServerWorld().getTime(),
                player.getServerWorld(), stack));
    }

    /** Handles {@link SwordfishStabC2SPayload}; the payload is untrusted. / 处理刺杀包；包内容不可信。 */
    public static void use(ServerPlayerEntity attacker, int targetEntityId) {
        if (attacker == null) {
            return;
        }
        // Remove before validation/callbacks: even a rejected or replayed request cannot reuse this release.
        // 在校验与回调前移除：被拒绝或重放的数据包也无法复用这次蓄力。
        QualifiedRelease release = RELEASES.remove(attacker.getUuid());
        ItemStack stack = attacker.getMainHandStack();
        if (release == null || !SwordfishRules.qualifiedRelease(release.heldTicks(),
                attacker.getServerWorld().getTime() - release.releasedAt(),
                attacker.getServerWorld() == release.world(), stack == release.stack())
                || !canUse(attacker, stack)) {
            return;
        }

        Entity entity = attacker.getServerWorld().getEntityById(targetEntityId);
        if (entity instanceof ServerPlayerEntity target) {
            if (!validPlayerGeometry(attacker, target)) {
                return;
            }
            // Recheck the nearer-device seam on the server; a forged player id cannot stab through a device.
            // 服务端重查更近设备；伪造玩家 id 不能穿过设备刺人。
            Vec3d eye = attacker.getEyePos();
            SeekerDeviceEntity blocker = SeekerDeviceRaycast.blockingDevice(attacker, eye,
                    eye.add(attacker.getRotationVec(0.0F).multiply(FisherRules.SWORDFISH_REACH)), target);
            if (blocker != null) {
                entity = blocker;
            }
        }
        if (entity instanceof SeekerDeviceEntity device) {
            if (SeekerDeviceHits.onSwordfishStab(attacker, device)) {
                consume(attacker, stack);
                playStab(attacker, device);
            }
            return;
        }
        if (!(entity instanceof ServerPlayerEntity target)) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(attacker.getWorld());
        if (!SparkFactionApi.canAffectPlayer(attacker, target, SparkWitchDeathReasons.SWORDFISH_STAB, game)
                || SparkTraitsKillerBridge.isLastEscapeActive(target)) {
            return;
        }

        Identifier attackerFaction = SparkFactionApi.resolveEffectiveFaction(attacker, game);
        Identifier victimFaction = SparkFactionApi.resolveEffectiveFaction(target, game);
        boolean deadBefore = game.isPlayerDead(target.getUuid());
        // This facade parries non-knife melee too. Query before costs while the exact stack is still nonempty;
        // Q4 consumes an accepted hit even when parried, shielded, revived, or met by Tofana retaliation.
        // 此门面也格挡非刀近战；在原栈尚非空、扣费前查询。Q4：有效命中被格挡、护盾、复活或托法娜反击也消耗。
        boolean parried = SparkTraitsKillerBridge.shouldCancelMeleeAttack(attacker, target, stack);
        consume(attacker, stack);
        playStab(attacker, target);
        if (parried) {
            return;
        }
        GameFunctions.killPlayer(target, true, attacker, SparkWitchDeathReasons.SWORDFISH_STAB);
        if (SwordfishRules.shouldPunish(attackerFaction, victimFaction, deadBefore,
                game.isPlayerDead(target.getUuid()), WitchFactorTraitsBridge.isDeathIntercepted(target),
                SparkTraitsKillerBridge.isNonFinalKillPending(target, attacker))
                && GameFunctions.isPlayerPlayingAndAlive(attacker)
                && GameFunctions.isPlayerAliveAndSurvival(attacker)) {
            GameFunctions.killPlayer(attacker, true, null, GameConstants.DeathReasons.SHOT_INNOCENT);
        }
    }

    static boolean canUse(ServerPlayerEntity player, ItemStack stack) {
        // A transferable weapon, not a role skill: silence/fear do not block ordinary knife use either.
        // 可转交的武器而非职业技能：沉默/恐惧同样不封锁普通刀的使用。
        return isParticipant(player) && stack != null && !stack.isEmpty()
                && stack == player.getMainHandStack() && stack.isOf(SparkWitchItems.swordfish())
                && !player.getItemCooldownManager().isCoolingDown(stack.getItem())
                && !ControlExpertStun.isStunned(player)
                && !SeekerRemoteSessionService.isLocked(player)
                && !SparkTraitsKillerBridge.blocksWeaponAction(player, stack);
    }

    private static boolean isParticipant(ServerPlayerEntity player) {
        return player != null && !player.isDisconnected() && player.isAlive()
                && GameWorldComponent.KEY.get(player.getWorld()).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE
                && GameFunctions.isPlayerPlayingAndAlive(player) && GameFunctions.isPlayerAliveAndSurvival(player)
                && !WraithStateService.isActive(player);
    }

    private static boolean validPlayerGeometry(ServerPlayerEntity attacker, ServerPlayerEntity target) {
        return SwordfishRules.acceptsPlayerHit(isParticipant(target), attacker.getUuid().equals(target.getUuid()),
                attacker.squaredDistanceTo(target), attacker.canSee(target));
    }

    private static void consume(ServerPlayerEntity attacker, ItemStack stack) {
        // Capture/consume before nested kill hooks can move or clear the attacker's inventory.
        // 在嵌套击杀钩子移动或清空攻击者背包前，消耗已捕获的原栈。
        stack.decrement(1);
        attacker.getInventory().markDirty();
        attacker.currentScreenHandler.sendContentUpdates();
    }

    private static void playStab(ServerPlayerEntity attacker, Entity target) {
        target.playSound(WatheSounds.ITEM_KNIFE_STAB, 1.0F, 1.0F);
        attacker.swingHand(Hand.MAIN_HAND, true);
    }

    public static void clearPlayer(ServerPlayerEntity player) {
        if (player != null) {
            RELEASES.remove(player.getUuid());
        }
    }

    private record QualifiedRelease(int heldTicks, long releasedAt, ServerWorld world, ItemStack stack) {
    }
}
