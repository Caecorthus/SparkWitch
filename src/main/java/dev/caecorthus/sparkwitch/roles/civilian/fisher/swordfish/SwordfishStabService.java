package dev.caecorthus.sparkwitch.roles.civilian.fisher.swordfish;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.SparkStrengthPartygoerCompat;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.FisherRules;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceRaycast;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteSessionService;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowEntity;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.hit.BlackRavenCrowHits;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlaybackEntity;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPuppetHits;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.caecorthus.sparkwitch.util.RoundParticipants;
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
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server validation of a Swordfish stab, consumption and friendly-fire death. / 剑鱼刺杀校验、消耗与小脑。 */
public final class SwordfishStabService {
    private static final Map<UUID, QualifiedRelease> RELEASES = new HashMap<>();
    private static boolean registered;

    private SwordfishStabService() {
    }

    public static synchronized void register() {
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
        if (!SwordfishRules.qualifiedServerHold(heldTicks) || !player.isUsingItem()
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

        Entity submitted = attacker.getServerWorld().getEntityById(targetEntityId);
        // Raven Form seam: a crow the server's own ray enters before every other candidate stands in for its owner, so
        // the stab below (costs, parry, kill, friendly-fire death) runs on the Black Raven.
        // 化鸦接缝：服务端自身射线先于其他所有候选进入的乌鸦以其主人代入，因此下方的刺击（消耗、格挡、击杀、误伤致死）作用于黑羽鸦。
        ServerPlayerEntity crowOwner = resolveAimedCrow(attacker, submitted);
        Entity entity = crowOwner != null ? crowOwner : resolveAimedTarget(attacker, submitted);
        if (entity instanceof SeekerDeviceEntity device) {
            if (SeekerDeviceHits.onSwordfishStab(attacker, device)) {
                consume(attacker, stack);
                playStab(attacker, device);
            }
            return;
        }
        // Magician seam: a puppet takes an accepted stab like its copied player (consumed, stab played, no
        // friendly-fire death). / 魔术师接缝：皮套与其复制的玩家一样承受被接纳的刺击（消耗、播放刺击，无误伤致死）。
        if (entity instanceof MagicianPlaybackEntity puppet) {
            if (MagicianPuppetHits.onSwordfishStab(attacker, puppet)) {
                consume(attacker, stack);
                playStab(attacker, puppet);
            }
            return;
        }
        // SparkStrength Partygoer seam (owner rule 2026-10-21): it dies to an accepted stab, which then costs the
        // Swordfish and plays the stab like a player hit; never friendly fire (a Partygoer is never an innocent).
        // SparkStrength 派对客接缝（所有者规则 2026-10-21）：被接纳的刺击打死它，随后与刺中玩家一样消耗剑鱼并播放刺击；
        // 永不触发小脑（派对客永远不是好人）。
        if (SparkStrengthPartygoerCompat.isPartygoer(entity)) {
            if (SparkStrengthPartygoerCompat.killByWeapon(attacker, entity, SparkWitchDeathReasons.SWORDFISH_STAB)) {
                consume(attacker, stack);
                playStab(attacker, entity);
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

    // The Fisher's living participant, then status exactly ACTIVE, then not an active Wraith.
    // 钓鱼佬的存活参与者，随后状态恰为 ACTIVE，再随后非激活冤魂。
    private static boolean isParticipant(ServerPlayerEntity player) {
        return RoundParticipants.SWORDFISH_TARGET.test(player);
    }

    private static boolean validPlayerGeometry(ServerPlayerEntity attacker, ServerPlayerEntity target) {
        return SwordfishRules.acceptsPlayerHit(isParticipant(target), attacker.getUuid().equals(target.getUuid()),
                attacker.squaredDistanceTo(target), attacker.canSee(target));
    }

    private static @Nullable Entity resolveAimedTarget(ServerPlayerEntity attacker, @Nullable Entity submitted) {
        if (!isAimCandidate(attacker, submitted)) {
            return null;
        }
        Vec3d start = attacker.getEyePos();
        Vec3d end = aimEnd(attacker, start);
        if (SwordfishRules.entryDistanceSquared(start, end, aimBox(submitted)) < 0.0) {
            return null;
        }

        // Compare all eligible players and foreign devices on THIS ray, never on a ray toward the submitted id.
        // 在这一条射线上比较所有合格玩家与他人设备；绝不向数据包所报目标另发一条射线。
        List<Entity> candidates = rayCandidates(attacker, start, end);
        Entity nearest = SwordfishRules.nearestOnRay(start, end, candidates, SwordfishStabService::aimBox);
        // A nearer device intercepts; a different nearer player rejects the request instead of changing the victim.
        // 更近设备拦截；若另一个玩家更近则拒绝请求，不擅自更换受害者。
        if (nearest == submitted || nearest instanceof SeekerDeviceEntity) {
            return nearest;
        }
        // The client picks a Partygoer with a thinner aim box than this one; a near tie (its carried prey overlaps it)
        // keeps the Partygoer instead of wasting the released charge. / 客户端选派对客的瞄准盒比这里更薄；近乎等距时（其扛着的
        // 猎物与它重叠）保留派对客，而不是白白浪费已松开的蓄力。
        if (nearest != null && SparkStrengthPartygoerCompat.isPartygoer(submitted)
                && SwordfishRules.withinPartygoerTie(
                SwordfishRules.entryDistanceSquared(start, end, aimBox(submitted)),
                SwordfishRules.entryDistanceSquared(start, end, aimBox(nearest)))) {
            return submitted;
        }
        return null;
    }

    /**
     * A crow submitted as the target: accepted when the server's own ray enters its lag-compensated volumes (grown by
     * {@link SwordfishRules#AIM_TOLERANCE}) strictly before every other candidate on that ray; returns the substituted
     * owner, else null (a refused crow stab does nothing, like a refused player stab).
     * 提交的目标为乌鸦时：服务端自身射线严格早于该射线上其他所有候选进入其延迟补偿体积（按 {@link SwordfishRules#AIM_TOLERANCE}
     * 扩大）则接受，返回被替身的主人；否则为 null（被拒的乌鸦刺击与被拒的玩家刺击一样毫无作用）。
     */
    private static @Nullable ServerPlayerEntity resolveAimedCrow(ServerPlayerEntity attacker,
                                                                 @Nullable Entity submitted) {
        if (!(submitted instanceof BlackRavenCrowEntity)) {
            return null;
        }
        Vec3d start = attacker.getEyePos();
        Vec3d end = aimEnd(attacker, start);
        double beat = Double.POSITIVE_INFINITY;
        for (Entity candidate : rayCandidates(attacker, start, end)) {
            double entry = SwordfishRules.entryDistanceSquared(start, end, aimBox(candidate));
            if (entry >= 0.0 && entry < beat) {
                beat = entry;
            }
        }
        return BlackRavenCrowHits.onSwordfishStab(attacker, submitted, start, end, SwordfishRules.AIM_TOLERANCE, beat);
    }

    /** The stab ray's end: the Swordfish reach along the look, cut at the first block. / 刺击射线终点。 */
    private static Vec3d aimEnd(ServerPlayerEntity attacker, Vec3d start) {
        Vec3d end = start.add(attacker.getRotationVec(1.0F).multiply(FisherRules.SWORDFISH_REACH));
        HitResult block = attacker.getWorld().raycast(new RaycastContext(start, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, attacker));
        return block.getType() != HitResult.Type.MISS ? block.getPos() : end;
    }

    /** Eligible players, foreign devices, puppets and Partygoers around the stab ray. / 刺击射线附近的合格玩家、他人设备、皮套与派对客。 */
    private static List<Entity> rayCandidates(ServerPlayerEntity attacker, Vec3d start, Vec3d end) {
        Box search = new Box(start, end).expand(1.0);
        var candidates = new ArrayList<Entity>();
        candidates.addAll(attacker.getServerWorld().getEntitiesByClass(ServerPlayerEntity.class, search,
                target -> isAimCandidate(attacker, target)));
        candidates.addAll(attacker.getServerWorld().getEntitiesByClass(SeekerDeviceEntity.class, search,
                device -> isAimCandidate(attacker, device)));
        // Magician puppets compete as players do (a nearer puppet rejects a stab submitted on a player behind it).
        // 魔术师皮套与玩家同等竞争（更近的皮套会使提交给其后方玩家的刺击被拒绝）。
        candidates.addAll(attacker.getServerWorld().getEntitiesByClass(MagicianPlaybackEntity.class, search,
                puppet -> isAimCandidate(attacker, puppet)));
        // SparkStrength Partygoers compete as players do too (listed last, so an exact tie keeps the earlier candidate).
        // SparkStrength 派对客同样与玩家同等竞争（列在最后，完全等距时保留先列出的候选）。
        candidates.addAll(attacker.getServerWorld().getOtherEntities(attacker, search,
                partygoer -> SparkStrengthPartygoerCompat.isPartygoer(partygoer)
                        && isAimCandidate(attacker, partygoer)));
        return candidates;
    }

    private static boolean isAimCandidate(ServerPlayerEntity attacker, @Nullable Entity entity) {
        if (entity == null || entity.getWorld() != attacker.getWorld()) {
            return false;
        }
        if (entity instanceof ServerPlayerEntity target) {
            return validPlayerGeometry(attacker, target);
        }
        if (entity instanceof MagicianPlaybackEntity) {
            return MagicianPuppetHits.isHittablePuppet(attacker, entity);
        }
        if (SparkStrengthPartygoerCompat.isPartygoer(entity)) {
            return SwordfishRules.acceptsPartygoerHit(SparkStrengthPartygoerCompat.isKillable(entity),
                    attacker.squaredDistanceTo(entity), attacker.canSee(entity));
        }
        return entity instanceof SeekerDeviceEntity device && device.isAlive() && !device.isRemoved()
                && !SeekerDeviceRaycast.isOwnDevice(attacker, device);
    }

    private static Box aimBox(Entity entity) {
        double margin = entity instanceof SeekerDeviceEntity device ? Math.max(0.0, device.targetingMargin()) : 0.0;
        return entity.getBoundingBox().expand(margin + SwordfishRules.AIM_TOLERANCE);
    }

    private static void consume(ServerPlayerEntity attacker, ItemStack stack) {
        // Capture/consume before nested kill hooks can move or clear the attacker's inventory.
        // 在嵌套击杀钩子移动或清空攻击者背包前，消耗已捕获的原栈。
        stack.decrement(1);
        attacker.getInventory().markDirty();
        attacker.currentScreenHandler.sendContentUpdates();
    }

    private static void playStab(ServerPlayerEntity attacker, Entity target) {
        // A crow stand-in hears the stab where the crow was hit. / 乌鸦代入时在乌鸦被命中处播放刺击声。
        if (!BlackRavenCrowHits.playAtSubstitutedCrow(target, WatheSounds.ITEM_KNIFE_STAB, 1.0F, 1.0F)) {
            target.playSound(WatheSounds.ITEM_KNIFE_STAB, 1.0F, 1.0F);
        }
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
