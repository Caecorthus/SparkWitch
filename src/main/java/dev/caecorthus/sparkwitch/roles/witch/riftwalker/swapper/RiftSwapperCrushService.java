package dev.caecorthus.sparkwitch.roles.witch.riftwalker.swapper;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.RiftwalkerRules;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateRecord;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate.RiftGateRegistry;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.agmas.noellesroles.AbilityPlayerComponent;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.taotie.SwallowedPlayerComponent;
import org.jetbrains.annotations.Nullable;

import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

/**
 * NoellesRoles Swapper vs. a Rift occupant (D13, C4, C7, C8; plan §16, research 06): a SparkWitch-owned HEAD inject on
 * the Swapper server handler cancels any swap that involves a player inside a gate and kills the Swapper with the
 * forced, terminal, killer-less death reason {@code RiftwalkerRules.PORTAL_CRUSHED_DEATH_REASON}, body at the
 * occupant's gate. Registers that reason as SparkTraits-terminal at {@code SERVER_STARTING}. Owned by P9 (with its
 * server mixin and the client Swapper widget patch).
 * NoellesRoles 交换者对门内玩家（D13、C4、C7、C8；plan §16，调研 06）：SparkWitch 自有的 HEAD 注入挂在交换者服务端
 * 处理器上，取消任何涉及门内玩家的交换，并以强制、终结、无凶手的死因 {@code RiftwalkerRules.PORTAL_CRUSHED_DEATH_REASON}
 * 处死交换者，尸体落在门内玩家所在的门前。在 {@code SERVER_STARTING} 时把该死因注册为 SparkTraits 终结死因。
 * 归属 P9（含其服务端 mixin 与客户端交换界面补丁）。
 */
public final class RiftSwapperCrushService {
    private static boolean registered;

    private RiftSwapperCrushService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerGlobalEventFormatter(RiftSwapperCrushRules.REPLAY_EVENT_ID,
                RiftSwapperCrushService::formatReplay);
        // Third owner-approved forced-terminal exception (D13/C8, after bell_toll and time_stolen): SparkTraits reads
        // terminal reasons from a live set at kill time, so registering on server start is early enough and idempotent;
        // an absent or older SparkTraits registers nothing (fail-closed: the vetoed-kill revert below takes over).
        // 第三个所有者批准的强制终结例外（D13/C8，继丧钟与时光耗尽之后）：SparkTraits 在击杀时实时读取终结原因集合，
        // 服务端启动时注册即足够早且幂等；缺失或旧版 SparkTraits 不注册（失败关闭：由下方被否决时的回退处理）。
        ServerLifecycleEvents.SERVER_STARTING.register(server ->
                SparkTraitsKillerBridge.registerTerminalDeathReason(RiftwalkerRules.PORTAL_CRUSHED_DEATH_REASON));
    }

    /**
     * Called from {@code RiftSwapperCrushMixin} at the HEAD of NoellesRoles' Swapper handler, on the server thread,
     * after the SparkFactionAPI and SparkTraits HEAD guards let the payload through. Mirrors NoellesRoles' own early
     * returns exactly (same world lookups, {@code isRole} so an acting overlay is followed), then applies
     * {@link RiftSwapperCrushRules#decide}. Any spectator target that is not an occupant — alive (Last Stand, Depression
     * fake death) or dead (the client's alive list lags up to 1 s behind a death) — only cancels the swap (C7).
     * Deliberately no cooldown check: NR has none server-side, so skipping the crush on cooldown would let a crafted
     * packet pull an occupant out. Returns whether NR's handler must be cancelled.
     * 由 {@code RiftSwapperCrushMixin} 在 NoellesRoles 交换处理器开头（服务端线程）调用，此时 SparkFactionAPI 与
     * SparkTraits 的 HEAD 守卫已放行。逐条复刻 NR 自身的提前返回（相同的世界查询；用 {@code isRole} 以跟随扮演覆盖），再按
     * {@link RiftSwapperCrushRules#decide} 处理。不在门内的旁观者目标——无论活着（背水一战、抑郁假死）还是已死亡（客户端
     * 存活列表比死亡最多滞后 1 秒）——只会取消交换（C7）。刻意不检查冷却：NR 服务端也不检查，否则伪造数据包可把门内的人
     * 拉出来。返回是否必须取消 NR 处理器。
     */
    public static boolean intercept(ServerPlayerEntity actor, @Nullable UUID firstId, @Nullable UUID secondId) {
        if (actor == null) {
            return false;
        }
        World world = actor.getWorld();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        PlayerEntity first = firstId == null ? null : world.getPlayerByUuid(firstId);
        PlayerEntity second = secondId == null ? null : world.getPlayerByUuid(secondId);
        boolean swapWouldRun = game.isRole(actor, Noellesroles.SWAPPER)
                && GameFunctions.isPlayerPlayingAndAlive(actor)
                && !SwallowedPlayerComponent.isPlayerSwallowed(actor)
                && first != null && second != null
                && !SwallowedPlayerComponent.isPlayerSwallowed(first)
                && !SwallowedPlayerComponent.isPlayerSwallowed(second);
        RiftSwapperCrushRules.Outcome outcome = RiftSwapperCrushRules.decide(swapWouldRun,
                swapWouldRun && RiftSessionService.isInside(first),
                swapWouldRun && RiftSessionService.isInside(second),
                swapWouldRun && first.isSpectator(),
                swapWouldRun && second.isSpectator());
        if (outcome.crushes()) {
            PlayerEntity host = outcome == RiftSwapperCrushRules.Outcome.CRUSH_AT_FIRST ? first : second;
            PlayerEntity other = host == first ? second : first;
            if (host instanceof ServerPlayerEntity occupant) {
                crush(actor, occupant, other);
            }
        }
        return outcome.cancelsSwap();
    }

    /**
     * Replay line, cue, occupant notice, then teleport-and-kill (research 06 §4.3 A): the Swapper stands on a safe cell
     * in front of the occupant's gate looking straight down, so Wathe's own body, drops and death spectator start all
     * land on that cell; forced, no killer (C4: no kill money, mana or credit). If the kill is somehow vetoed the
     * Swapper goes back where they stood and gets NR's normal cooldown; the swap stays cancelled either way.
     * 回放、演出、门内提示，然后传送并处死（调研 06 §4.3 方案 A）：交换者站到门前安全格、视线朝下，Wathe 自己生成的
     * 尸体、掉落物与死亡旁观起点都落在该格；强制、无凶手（C4：没有击杀金币、魔力与功劳）。若处死意外被否决，交换者被
     * 送回原位并获得 NR 正常冷却；交换无论如何都被取消。
     */
    private static void crush(ServerPlayerEntity swapper, ServerPlayerEntity occupant, @Nullable PlayerEntity other) {
        ServerWorld gateWorld = occupant.getServerWorld();
        OptionalInt gateNumber = RiftSessionService.currentGate(occupant);
        RiftGateRecord gate = gateNumber.isPresent()
                ? RiftGateRegistry.byNumber(gateWorld, gateNumber.getAsInt()).orElse(null)
                : null;
        // The gate can vanish in the same tick; the occupant's body is anchored on it, so fall back to that anchor.
        // 门可能在同一 tick 消失；门内玩家的身体固定在门上，因此退回到该锚点。
        Vec3d gatePos = gate != null ? gate.pos() : occupant.getPos();
        Direction facing = gate != null ? gate.facing() : occupant.getHorizontalFacing();

        recordReplay(swapper, occupant, other, gateNumber.orElse(0));
        playCue(gateWorld, gatePos);
        notifyOccupant(occupant);
        if (other instanceof ServerPlayerEntity second && second != occupant && RiftSessionService.isInside(second)) {
            notifyOccupant(second);
        }

        ServerWorld originWorld = swapper.getServerWorld();
        Vec3d origin = swapper.getPos();
        float originYaw = swapper.getYaw();
        float originPitch = swapper.getPitch();
        Vec3d cell = RiftSwapperBodyCell.find(swapper, gateWorld, gatePos, facing);
        if (cell == null) {
            // No safe cell in front of the gate: die where they stand rather than inside a wall or on the gate.
            // 门前没有安全格：原地死亡，而不是卡进墙里或压在门上。
            SparkWitch.LOGGER.warn("No safe portal-crush body cell in front of gate {} for {}",
                    gateNumber.orElse(0), swapper.getUuid());
            swapper.teleport(originWorld, origin.x, origin.y, origin.z, Set.of(), originYaw, 90.0F);
        } else {
            swapper.teleport(gateWorld, cell.x, cell.y, cell.z, Set.of(), facing.asRotation(), 90.0F);
        }
        GameFunctions.killPlayer(swapper, true, null, RiftwalkerRules.PORTAL_CRUSHED_DEATH_REASON, true);
        if (GameFunctions.isPlayerPlayingAndAlive(swapper)) {
            // Should not happen (forced + SparkTraits-terminal); e.g. an older SparkTraits Last Escape. Undo the move.
            // 不应发生（强制且为 SparkTraits 终结死因），例如旧版 SparkTraits 的脱险。撤销移动。
            swapper.teleport(originWorld, origin.x, origin.y, origin.z, Set.of(), originYaw, originPitch);
            AbilityPlayerComponent.KEY.get(swapper).setCooldown(RiftSwapperCrushRules.VETOED_CRUSH_COOLDOWN_TICKS);
        }
    }

    /**
     * A SparkWitch global event, never NR's {@code recordSkillUse}: NR's formatter always prints "swapped A and B", and
     * the Judge and Wraith observers key off that record. Written before the kill so the replay reads attempt → death.
     * 使用 SparkWitch 全局事件，绝不使用 NR 的 {@code recordSkillUse}：NR 格式化器总会写成「交换了 A 和 B」，且法官与
     * 冤魂的观察者依赖该记录。在处死前写入，回放顺序为「尝试 → 死亡」。
     */
    private static void recordReplay(ServerPlayerEntity swapper, ServerPlayerEntity occupant,
                                     @Nullable PlayerEntity other, int gateNumber) {
        NbtCompound data = new NbtCompound();
        data.putUuid("actor", swapper.getUuid());
        data.putUuid("target", occupant.getUuid());
        if (other != null && other != occupant) {
            data.putUuid("other", other.getUuid());
        }
        data.putInt("gate", gateNumber);
        GameRecordManager.recordGlobalEvent(occupant.getServerWorld(), RiftSwapperCrushRules.REPLAY_EVENT_ID,
                swapper, data);
    }

    private static @Nullable Text formatReplay(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                               ServerWorld world) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor") || !data.containsUuid("target")) {
            return null;
        }
        var playerInfo = ReplayGenerator.getPlayerInfoCache(match);
        return Text.translatable(RiftSwapperCrushRules.REPLAY_KEY,
                ReplayGenerator.formatPlayerName(data.getUuid("actor"), playerInfo),
                ReplayGenerator.formatPlayerName(data.getUuid("target"), playerInfo));
    }

    /** 「咔嚓」 at the gate, audible nearby, with a burst of particles. / 门口一声「咔嚓」，附近可闻，并有一团粒子。 */
    private static void playCue(ServerWorld world, Vec3d gatePos) {
        double x = gatePos.x;
        double y = gatePos.y + 1.0;
        double z = gatePos.z;
        world.playSound(null, x, y, z, SoundEvents.BLOCK_BONE_BLOCK_BREAK, SoundCategory.PLAYERS,
                RiftSwapperCrushRules.CRUNCH_VOLUME, RiftSwapperCrushRules.CRUNCH_PITCH);
        world.playSound(null, x, y, z, SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS,
                RiftSwapperCrushRules.SNAP_VOLUME, RiftSwapperCrushRules.SNAP_PITCH);
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, x, y, z,
                RiftSwapperCrushRules.PORTAL_PARTICLES, 0.35, 0.8, 0.35, 0.05);
        world.spawnParticles(ParticleTypes.CRIT, x, y, z,
                RiftSwapperCrushRules.CRIT_PARTICLES, 0.3, 0.6, 0.3, 0.2);
    }

    private static void notifyOccupant(ServerPlayerEntity occupant) {
        occupant.sendMessage(Text.translatable(RiftSwapperCrushRules.OCCUPANT_MESSAGE_KEY), true);
    }
}
