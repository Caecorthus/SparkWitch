package dev.caecorthus.sparkwitch.roles.killer.ninja;

import dev.caecorthus.sparkwitch.SparkWitchEntities;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.NoellesTaotieSeekerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsSeekerBridge;
import dev.caecorthus.sparkwitch.entity.NinjaGrapplingHookEntity;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteSessionService;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperControlComponent;
import dev.caecorthus.sparkwitch.util.OffMatchUse;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-only owner of the Grappling Hook cycle: one active hook per player (UUID to hook), the throw, and its
 * cleanup on death, reset, role change and round end. Role-agnostic like the Kunai: anyone holding the item may use it.
 * 钩爪循环的仅服务端所有者：每名玩家最多一个活动钩爪（UUID 到钩爪），负责投掷，以及死亡、重置、职业变更与回合结束时的
 * 清理。与苦无一样不限职业：任何持有者都能使用。
 */
public final class NinjaGrappleService {
    private static final Map<UUID, NinjaGrapplingHookEntity> ACTIVE_HOOKS = new HashMap<>();
    private static boolean registered;

    private NinjaGrappleService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ResetPlayer.EVENT.register(NinjaGrappleService::discardHook);
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                discardAll(serverWorld);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ACTIVE_HOOKS.clear());
    }

    /**
     * Item right-click on the server. A live hook takes every press: a latched one starts the pull, a flying or
     * pulling one swallows it. Otherwise a hook is thrown when the item is ready, the thrower may grapple
     * ({@link #mayGrapple}) and is not held. Returns whether the press was consumed.
     * 服务端物品右键。存在活动钩爪时接收每次按键：已钩住则开始拉拽，飞行或拉拽中则吞掉按键。否则在物品就绪、投掷者可以使用
     * 钩爪（{@link #mayGrapple}）且未被持有时投出钩爪。返回该按键是否被消耗。
     */
    public static boolean use(ServerPlayerEntity player) {
        // Magician replay stand-ins share the Magician's UUID; a replayed hook would chain and pull the real player.
        // 魔术师回放替身与魔术师共用 UUID；回放出的钩爪会连到并拉动真正的玩家。
        if (player instanceof FakePlayer) {
            return false;
        }
        NinjaGrapplingHookEntity hook = activeHook(player);
        if (hook != null) {
            if (hook.getState() == NinjaGrapplingHookEntity.State.LATCHED && !isHeld(player)) {
                hook.startPull(player);
            }
            return true;
        }
        if (player.getItemCooldownManager().isCoolingDown(SparkWitchItems.ninjaGrapplingHook())
                || !mayGrapple(player)
                || isHeld(player)) {
            return false;
        }
        ServerWorld world = player.getServerWorld();
        NinjaGrapplingHookEntity thrown = new NinjaGrapplingHookEntity(world, player);
        if (!world.spawnEntity(thrown)) {
            return false;
        }
        ACTIVE_HOOKS.put(player.getUuid(), thrown);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_ARMOR_EQUIP_CHAIN.value(), SoundCategory.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    public static @Nullable NinjaGrapplingHookEntity activeHook(ServerPlayerEntity player) {
        NinjaGrapplingHookEntity hook = ACTIVE_HOOKS.get(player.getUuid());
        if (hook != null && hook.isRemoved()) {
            // Unloaded or otherwise removed without a callback. / 已卸载或未经回调被移除。
            ACTIVE_HOOKS.remove(player.getUuid(), hook);
            return null;
        }
        return hook;
    }

    /**
     * The throw and chain gate ({@link NinjaRules#mayGrapple}): a match participant ({@link
     * OffMatchUse#isMatchParticipant}) must be playing, alive and in survival; anyone else, such as a player outside a
     * match, may grapple while alive and not a spectator.
     * 投掷与铁链门槛（{@link NinjaRules#mayGrapple}）：对局参与者（{@link OffMatchUse#isMatchParticipant}）必须在局、存活且
     * 为生存模式；其他人（例如局外玩家）只要存活且不是旁观者即可使用钩爪。
     */
    public static boolean mayGrapple(ServerPlayerEntity player) {
        return NinjaRules.mayGrapple(
                OffMatchUse.isMatchParticipant(player),
                GameFunctions.isPlayerPlayingAndAlive(player) && GameFunctions.isPlayerAliveAndSurvival(player),
                player.isAlive() && !player.isSpectator()
        );
    }

    /**
     * Another authority holds the body or the player is locked (the Rift session's private list, copied on purpose,
     * plus the Hunter trap root): Taotie swallow, Last Stand, Last Escape, Kidnapper control, Control Expert stun, a
     * Seeker session, a foreign camera, or a Hunter root. A held player can neither throw nor pull, and an active hook's
     * chain breaks, so a pull never moves a held body out of its hold and a chain never points at an invisible Last
     * Escape player.
     * 本体被其他机制持有或玩家被锁定（裂隙会话的私有列表，有意复制，另加猎人陷阱定身）：饕餮吞下、背水一战、脱险、绑架者
     * 控制、控场专家眩晕、搜寻者会话、镜头被占用或被猎人定身。被持有的玩家既不能投掷也不能拉拽，活动钩爪的铁链会断开，
     * 因此拉拽绝不会把被持有的身体拉出控制，铁链也不会指向脱险中隐身的玩家。
     */
    public static boolean isHeld(ServerPlayerEntity player) {
        KidnapperControlComponent kidnap = KidnapperControlComponent.KEY.getNullable(player);
        HunterPlayerComponent hunter = HunterPlayerComponent.KEY.getNullable(player);
        return NoellesTaotieSeekerBridge.isSwallowed(player)
                || SparkTraitsSeekerBridge.isLastStandPending(player)
                || SparkTraitsKillerBridge.isLastEscapeActive(player)
                || kidnap != null && kidnap.isControlled()
                || ControlExpertStun.isStunned(player)
                || SeekerRemoteSessionService.isLocked(player)
                || player.getCameraEntity() != player
                || hunter != null && hunter.isRooted();
    }

    public static boolean holdsHook(PlayerEntity player) {
        return player.getMainHandStack().isOf(SparkWitchItems.ninjaGrapplingHook())
                || player.getOffHandStack().isOf(SparkWitchItems.ninjaGrapplingHook());
    }

    /** Silent cleanup (death, reset, role change): no sound, no cooldown. / 静默清理（死亡、重置、职业变更）：无音效、无冷却。 */
    public static void discardHook(ServerPlayerEntity player) {
        NinjaGrapplingHookEntity hook = ACTIVE_HOOKS.remove(player.getUuid());
        if (hook != null && !hook.isRemoved()) {
            hook.discardSilently();
        }
    }

    /** Called by the hook when it is removed on the server. / 钩爪在服务端被移除时调用。 */
    public static void forget(NinjaGrapplingHookEntity hook) {
        UUID owner = hook.getOwnerUuid();
        if (owner != null) {
            ACTIVE_HOOKS.remove(owner, hook);
        }
    }

    /**
     * Round-end sweep at Wathe's finalize, including hooks whose owner already left.
     * 在 Wathe 收尾时进行回合结束清理，包括持有者已离开的钩爪。
     */
    private static void discardAll(ServerWorld world) {
        for (NinjaGrapplingHookEntity hook : world.getEntitiesByType(
                SparkWitchEntities.ninjaGrapplingHook(), EntityPredicates.VALID_ENTITY)) {
            hook.discardSilently();
        }
        ACTIVE_HOOKS.values().removeIf(NinjaGrapplingHookEntity::isRemoved);
    }
}
