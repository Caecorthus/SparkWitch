package dev.caecorthus.sparkwitch.roles.killer.blackraven.crow;

import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.net.BlackRavenCrowCorrectionS2CPayload;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.net.BlackRavenCrowNetworking;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperControlComponent;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchFearService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Frozen contract: the server-owned Raven Form session. {@link #end} is the one way any feature ends a session;
 * {@link #killOwnerVia} is the one way a hit on the crow kills its owner. Architecture (plan §3.6): the player stays
 * its own server entity at the body (never riding, never the camera of the crow on the server), invisible and frozen,
 * while the owner's client switches its camera to the crow and streams poses; chunk view, chunk batches and entity
 * tracking follow the crow ({@link BlackRavenCrowStreaming}). Server thread only.
 * 冻结契约：服务端持有的化鸦会话。{@link #end} 是任何功能结束会话的唯一途径；{@link #killOwnerVia} 是乌鸦被命中时处死主人的唯一途径。
 * 架构（计划 §3.6）：玩家在服务端始终是位于本体处的自身实体（从不骑乘，服务端也不把相机切到乌鸦），隐身并冻结；主人客户端把镜头切到
 * 乌鸦并上报位姿；区块视野、区块批次与实体追踪跟随乌鸦（{@link BlackRavenCrowStreaming}）。仅服务端线程。
 */
public final class BlackRavenCrowSessions {
    private static final float START_CUE_VOLUME = 0.6F;

    private BlackRavenCrowSessions() {
    }

    // ---- Queries / 查询 ----

    /** Side-neutral: see {@link BlackRavenCrowForm#isInCrowForm}. / 两端通用。 */
    public static boolean isActive(@Nullable PlayerEntity player) {
        return BlackRavenCrowForm.isInCrowForm(player);
    }

    /** Server: the crow of the owner's live session, or null. / 服务端：主人有效会话的乌鸦，没有时为 null。 */
    public static @Nullable BlackRavenCrowEntity crowOf(@Nullable ServerPlayerEntity owner) {
        if (owner == null) {
            return null;
        }
        BlackRavenCrowPlayerComponent component = BlackRavenCrowPlayerComponent.KEY.getNullable(owner);
        if (component == null || !component.isActive()) {
            return null;
        }
        BlackRavenCrowEntity crow = findCrow(owner, component.getCrowEntityId());
        return crow != null && !crow.isRemoved() && crow.isOwnedBy(owner.getUuid()) ? crow : null;
    }

    /**
     * Server: the owner whose live session flies {@code crow}, or null for an orphan.
     * 服务端：正在操控 {@code crow} 的有效会话主人；孤儿乌鸦返回 null。
     */
    public static @Nullable ServerPlayerEntity ownerOf(@Nullable BlackRavenCrowEntity crow) {
        if (crow == null || crow.isRemoved()) {
            return null;
        }
        ServerPlayerEntity owner = crow.onlineOwner();
        if (owner == null) {
            return null;
        }
        BlackRavenCrowPlayerComponent component = BlackRavenCrowPlayerComponent.KEY.getNullable(owner);
        return component != null && component.isActive() && component.getCrowEntityId() == crow.getId()
                ? owner : null;
    }

    static boolean isLiveCrow(ServerWorld world, BlackRavenCrowEntity crow) {
        ServerPlayerEntity owner = ownerOf(crow);
        return owner != null && owner.getServerWorld() == world;
    }

    /**
     * Perception centre override (plan §3.5): the crow while the Raven flies one, else null (the player's own position).
     * 感知中心覆盖（计划 §3.5）：黑羽鸦正在操控乌鸦时取乌鸦位置，否则为 null（取玩家自身位置）。
     */
    static @Nullable Vec3d perceptionCentre(ServerPlayerEntity raven) {
        BlackRavenCrowEntity crow = crowOf(raven);
        return crow == null ? null : crow.getPos();
    }

    // ---- Start / 开始 ----

    /**
     * Starts a session for a player that passed {@link BlackRavenCrowRules#toggleVerdict}: spawns the crow at the
     * body's eye facing its look, hides and anchors the body, and streams the crow's surroundings.
     * 为已通过 {@link BlackRavenCrowRules#toggleVerdict} 的玩家开始会话：在本体眼睛处按其视线朝向生成乌鸦，隐藏并锚定本体，
     * 并流式加载乌鸦周围区域。
     */
    static boolean start(ServerPlayerEntity player, BlackRavenCrowPlayerComponent component) {
        ServerWorld world = player.getServerWorld();
        BlackRavenCrowEntity crow = BlackRavenCrowEntities.crow().create(world);
        if (crow == null) {
            return false;
        }
        crow.setOwner(player.getUuid());
        crow.refreshPositionAndAngles(player.getX(), BlackRavenCrowRules.crowYForEye(player.getEyeY()), player.getZ(),
                player.getYaw(), player.getPitch());
        // Spawn first: the owner is in range, so the spawn packet precedes the session sync.
        // 先生成：主人在追踪范围内，因此生成包先于会话同步到达。
        if (!world.spawnEntity(crow)) {
            return false;
        }
        long now = world.getTime();
        boolean ownsInvisibility = !player.hasStatusEffect(StatusEffects.INVISIBILITY);
        component.beginSession(crow.getId(), now, now + BlackRavenCrowRules.SESSION_MAX_TICKS, player.getPos(),
                ownsInvisibility);
        maintainInvisibility(player, component);
        player.setSprinting(false);
        if (player.isUsingItem()) {
            // Clear, never release: releasing a Wathe knife would stab. / 只清除、绝不释放：释放 Wathe 小刀会触发刺击。
            player.clearActiveItem();
        }
        BlackRavenCrowNetworking.sendCorrection(player, correctionOf(crow));
        BlackRavenCrowStreaming.onSessionStarted(player, crow);
        player.playSoundToPlayer(SoundEvents.ENTITY_PARROT_FLY, SoundCategory.PLAYERS, START_CUE_VOLUME, 0.8F);
        player.playSoundToPlayer(SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, START_CUE_VOLUME, 0.7F);
        GameRecordManager.recordSkillUse(player, BlackRavenCrowRules.SKILL_ID, null, null);
        BlackRavenCrowEvents.STARTED.invoker().onStarted(player, crow);
        return true;
    }

    // ---- End / 结束 ----

    /**
     * The one way any feature ends a session: removes the crow, restores the body's visibility, clears the session
     * fields, starts the 45 s cooldown when {@link BlackRavenCrowEndReason#startsCooldown()}, lets the view resettle on
     * the body, tells the owner, then fires {@link BlackRavenCrowEvents#ENDED}. Idempotent; a no-op without a session.
     * 任何功能结束会话的唯一途径：移除乌鸦、恢复本体可见、清空会话字段、在 startsCooldown 时开始 45 秒冷却、让视野回到本体、
     * 提示主人，最后触发 {@link BlackRavenCrowEvents#ENDED}。幂等；无会话时为空操作。
     */
    public static void end(@Nullable ServerPlayerEntity player, @Nullable BlackRavenCrowEndReason reason) {
        if (player == null || reason == null) {
            return;
        }
        BlackRavenCrowPlayerComponent component = BlackRavenCrowPlayerComponent.KEY.getNullable(player);
        if (component == null || !component.isActive()) {
            return;
        }
        BlackRavenCrowEntity crow = findCrow(player, component.getCrowEntityId());
        Vec3d crowPos = crow != null ? crow.getPos() : player.getEyePos();
        if (crow != null && !crow.isRemoved()) {
            crow.discard();
        }
        restoreBody(player, component);
        component.endSession();
        if (reason.startsCooldown()) {
            component.raiseCooldown(BlackRavenCrowRules.COOLDOWN_TICKS);
        }
        BlackRavenCrowStreaming.onSessionEnded(player);
        String key = reason.messageKey();
        if (key != null) {
            player.sendMessage(Text.translatable(key), true);
        }
        BlackRavenCrowEvents.ENDED.invoker().onEnded(player, reason, crowPos);
    }

    /**
     * Kill link for crow-hits: a lethal hit on the crow ends the session (no Homing Feather) and runs the standard
     * {@code GameFunctions.killPlayer(owner, true, killer, deathReason)}, so kill credit, Wathe's innocent-kill rules and
     * every protection (psycho armour, SparkTraits, Last Stand) apply as if the body had been hit; the corpse lies at
     * the body. An orphan crow is simply removed. Returns true when the owner is dead afterwards.
     * 供 crow-hits 使用的击杀接口：对乌鸦的致命命中先结束会话（不使用归羽），再执行标准的
     * {@code GameFunctions.killPlayer(owner, true, killer, deathReason)}，因此击杀归属、Wathe 误杀规则与所有保护（疯魔护甲、
     * SparkTraits、背水一战）都如同命中本体一样生效；尸体位于本体处。孤儿乌鸦直接移除。之后主人已死亡时返回 true。
     */
    public static boolean killOwnerVia(@Nullable BlackRavenCrowEntity crow, @Nullable ServerPlayerEntity killer,
                                       Identifier deathReason) {
        if (crow == null || crow.getWorld().isClient() || crow.isRemoved() || deathReason == null) {
            return false;
        }
        ServerPlayerEntity owner = ownerOf(crow);
        if (owner == null) {
            crow.discard();
            return false;
        }
        end(owner, BlackRavenCrowEndReason.CROW_KILLED);
        GameFunctions.killPlayer(owner, true, killer, deathReason);
        return !GameFunctions.isPlayerPlayingAndAlive(owner);
    }

    // ---- Per tick / 逐刻 ----

    /**
     * Component server tick: drops everything once the RAW role is no longer the Black Raven, otherwise ends the
     * session with the first failing exit check and keeps the body hidden.
     * 组件服务端刻：原始职业不再是黑羽鸦时清空一切；否则以第一个失败的退出检查结束会话，并保持本体隐身。
     */
    static void tick(ServerPlayerEntity player, BlackRavenCrowPlayerComponent component) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getServerWorld());
        if (!BlackRavenRules.isBlackRaven(game.getRole(player))) {
            end(player, BlackRavenCrowEndReason.ROLE_LOST);
            component.clear();
            return;
        }
        if (!component.isActive()) {
            return;
        }
        BlackRavenCrowEndReason reason = exitReason(player, component, game);
        if (reason != null) {
            end(player, reason);
            return;
        }
        if (player.isSprinting()) {
            player.setSprinting(false);
        }
        maintainInvisibility(player, component);
    }

    private static @Nullable BlackRavenCrowEndReason exitReason(ServerPlayerEntity player,
                                                                BlackRavenCrowPlayerComponent component,
                                                                GameWorldComponent game) {
        if (game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) {
            return BlackRavenCrowEndReason.ROUND_END;
        }
        if (!player.isAlive() || player.isSpectator() || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return BlackRavenCrowEndReason.DIED;
        }
        if (NoellesTaotieSeekerBridge.isSwallowed(player)) {
            return BlackRavenCrowEndReason.SWALLOWED;
        }
        if (ControlExpertStun.isStunned(player)) {
            return BlackRavenCrowEndReason.STUNNED;
        }
        KidnapperControlComponent kidnap = KidnapperControlComponent.KEY.getNullable(player);
        if (kidnap != null && kidnap.isControlled()) {
            return BlackRavenCrowEndReason.KIDNAPPED;
        }
        if (GrandWitchFearService.isPlayerFeared(player)) {
            return BlackRavenCrowEndReason.FEARED;
        }
        if (crowOf(player) == null) {
            return BlackRavenCrowEndReason.CROW_LOST;
        }
        if (player.hasVehicle() || player.isSleeping()
                || BlackRavenCrowRules.bodyMoved(component.getAnchor(), player.getPos())) {
            return BlackRavenCrowEndReason.BODY_MOVED;
        }
        if (component.tickUngrounded(BlackRavenCrowRules.isBodyGrounded(player.isOnGround(), player.hasVehicle(),
                player.isTouchingWater()))) {
            return BlackRavenCrowEndReason.BODY_MOVED;
        }
        if (BlackRavenCrowRules.sessionTimedOut(component.getSessionEndTick(), player.getServerWorld().getTime())) {
            return BlackRavenCrowEndReason.TIMEOUT;
        }
        return null;
    }

    // ---- Body / 本体 ----

    /**
     * Server invisibility without particles or icon, refreshed before it runs out; taken over when another source's
     * invisibility ended mid-session.
     * 无粒子、无图标的服务端隐身，在耗尽前刷新；若其他来源的隐身在会话中途结束，则由本会话接管。
     */
    private static void maintainInvisibility(ServerPlayerEntity player, BlackRavenCrowPlayerComponent component) {
        StatusEffectInstance current = player.getStatusEffect(StatusEffects.INVISIBILITY);
        if (current != null && !component.ownsInvisibility()) {
            return;
        }
        if (current == null) {
            component.takeInvisibilityOwnership();
        }
        if (current == null || current.getDuration() <= BlackRavenCrowRules.MAINTAINED_INVISIBILITY_TICKS / 2) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY,
                    BlackRavenCrowRules.MAINTAINED_INVISIBILITY_TICKS, 0, false, false, false));
        }
    }

    private static void restoreBody(ServerPlayerEntity player, BlackRavenCrowPlayerComponent component) {
        if (component.ownsInvisibility()) {
            player.removeStatusEffect(StatusEffects.INVISIBILITY);
        }
    }

    // ---- Lifecycle helpers / 生命周期辅助 ----

    /** Ends any session and wipes the component (role change, reset, round end). / 结束会话并清空组件。 */
    static void endAndClear(ServerPlayerEntity player, BlackRavenCrowEndReason reason) {
        end(player, reason);
        BlackRavenCrowPlayerComponent component = BlackRavenCrowPlayerComponent.KEY.getNullable(player);
        if (component != null) {
            component.clear();
        }
    }

    /** Round-end sweep of every crow in the world. / 回合结束时清扫该世界中的所有乌鸦。 */
    static void discardAll(ServerWorld world) {
        for (BlackRavenCrowEntity crow : world.getEntitiesByType(TypeFilter.instanceOf(BlackRavenCrowEntity.class),
                EntityPredicates.VALID_ENTITY)) {
            crow.discard();
        }
    }

    static BlackRavenCrowCorrectionS2CPayload correctionOf(BlackRavenCrowEntity crow) {
        return new BlackRavenCrowCorrectionS2CPayload(crow.getId(), crow.getX(), crow.getY(), crow.getZ(),
                crow.getYaw(), crow.getPitch());
    }

    private static @Nullable BlackRavenCrowEntity findCrow(ServerPlayerEntity owner, int entityId) {
        if (entityId < 0) {
            return null;
        }
        Entity entity = owner.getServerWorld().getEntityById(entityId);
        return entity instanceof BlackRavenCrowEntity crow ? crow : null;
    }
}
