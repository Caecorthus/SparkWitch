package dev.caecorthus.sparkwitch.roles.witch.abysslistener.zone;

import dev.caecorthus.sparkwitch.roles.witch.abysslistener.AbyssListenerRules;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.MapVariablesWorldComponent;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongFunction;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Deep Dark Zone runtime (design B, research/04 §3.3): client-only fake sculk/deepslate blocks. The server world never
 * changes; an in-memory per-world {@link DeepDarkZoneState} is the single authority that the standing effects read
 * through {@link #isConverted}. Fake looks reach clients through vanilla chunk-delta packets, a heal sweep resends
 * them every {@link #HEAL_INTERVAL_TICKS} ticks (re-tracked chunks, late joiners, spectators, vanilla interact
 * resends), and a cell whose real block changed is dropped. Nothing is persisted, so a crash needs no cleanup.
 * Standing effects, exposure and the sanity drain belong to {@code DeepDarkZoneStanding*} (L3b).
 * 深暗领域运行时（方案 B，research/04 §3.3）：仅发给客户端的假幽匿/深板岩方块。服务端世界从不改变；每个世界一份内存中的
 * {@link DeepDarkZoneState} 是唯一权威，站立效果通过 {@link #isConverted} 读取它。假外观通过原版区块增量包到达客户端，
 * 补发扫描每 {@link #HEAL_INTERVAL_TICKS} 刻重发一次（重新追踪的区块、后加入者、旁观者、原版交互回发），真实方块已改变的
 * 格子会被丢弃。不持久化任何内容，崩服无需清理。站立效果、暴露与理智下降属于 {@code DeepDarkZoneStanding*}（L3b）。
 */
public final class DeepDarkZoneService {
    /** Resend cadence for live fake looks. / 存活假外观的补发周期。 */
    static final int HEAL_INTERVAL_TICKS = 20;
    private static final Map<ServerWorld, Runtime> RUNTIMES = new IdentityHashMap<>();
    private static boolean registered;

    private DeepDarkZoneService() {
    }

    /**
     * True while the cell is converted by a live zone (convert tick <= now < restore tick). Frozen query for the
     * standing check (L3b); reads only the in-memory registry, never the world.
     * 当该格被存活领域转换时（转换 tick <= 当前 < 恢复 tick）为 true。供站立判定使用的冻结查询（L3b）；只读内存登记表，不读世界。
     */
    public static boolean isConverted(ServerWorld world, BlockPos pos) {
        Runtime runtime = RUNTIMES.get(world);
        return runtime != null && runtime.state.isConverted(pos.asLong(), world.getTime());
    }

    /** True while the world has at least one live zone. Frozen query (L3b). / 世界内是否有存活领域。冻结查询（L3b）。 */
    public static boolean hasActiveZones(ServerWorld world) {
        Runtime runtime = RUNTIMES.get(world);
        return runtime != null && runtime.state.hasZones();
    }

    /**
     * Throwers of the zones currently converting the cell (online or not), in throw order. The standing effects may
     * use the first online one as the actor and fall back to no actor (plan default N19: a zone outlives its thrower).
     * 当前转换该格的领域的投掷者（无论是否在线），按投掷顺序。站立效果可用第一个在线者作为施加者，否则不设施加者
     * （N19：投掷者死亡或离开后领域照常持续）。
     */
    public static List<UUID> ownersAt(ServerWorld world, BlockPos pos) {
        Runtime runtime = RUNTIMES.get(world);
        return runtime == null ? List.of() : runtime.state.ownersAt(pos.asLong(), world.getTime());
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerTickEvents.END_WORLD_TICK.register(DeepDarkZoneService::tickWorld);
        // Stale zones never reach a new round; a decided round restores at once instead of animating through the fade.
        // 过期领域永不带入新一局；决出胜负时立即恢复，而不是在淡出期间继续播放。
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> clearWorld(world));
        GameEvents.ON_WIN_DETERMINED.register((world, game, status, neutralWinner) -> clear(world));
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            clearWorld(world);
            if (world instanceof ServerWorld serverWorld) {
                DeepDarkSporeFlaskEntity.discardAll(serverWorld);
            }
        });
        // An unloading world has no player left to correct, so its registry is only forgotten.
        // 正在卸载的世界已没有需要纠正的玩家，因此只丢弃其登记表。
        ServerWorldEvents.UNLOAD.register((server, world) -> RUNTIMES.remove(world));
        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
                server.getWorlds().forEach(DeepDarkZoneService::clear));
    }

    /**
     * Opens a zone where a flask landed: one flood-fill snapshot at the landing tick. Every block converts at that tick
     * (owner D14: shown by this tick's end-of-tick advance) and restores on its own schedule. Only an ACTIVE round opens
     * zones, so a flask landing during the end fade does nothing.
     * 在孢瓶落点展开领域：落地时做一次泛洪快照。所有方块都在这一刻转换（D14：由本刻末尾的推进立即显示），之后按各自的
     * 时间表恢复。只有 ACTIVE 的对局会展开领域，因此在结束淡出期间落地的孢瓶不会产生任何效果。
     */
    static void open(ServerWorld world, @Nullable UUID owner, BlockPos landing) {
        if (!isRoundActive(world)) {
            return;
        }
        MapVariablesWorldComponent areas = MapVariablesWorldComponent.KEY.get(world);
        Box playArea = areas.getPlayArea();
        Box resetTemplateArea = areas.getResetTemplateArea();
        long now = world.getTime();
        List<DeepDarkZoneShape.Target> targets = DeepDarkZoneShape.collect(landing, AbyssListenerRules.ZONE_RADIUS,
                new DeepDarkZoneShape.CellProbe() {
                    @Override
                    public boolean passable(BlockPos pos) {
                        return DeepDarkZoneBlocks.passable(world, pos);
                    }

                    @Override
                    public boolean convertible(BlockPos pos) {
                        return DeepDarkZoneBlocks.convertible(world, pos, playArea, resetTemplateArea);
                    }
                });
        // A landing that converts nothing opens no zone: only the shatter cue, so no heartbeat and no standing scan.
        // 未转换任何方块的落点不展开领域：只播放碎裂表现，因此没有心跳，也没有站立检查。
        if (targets.isEmpty()) {
            DeepDarkZoneCues.landing(world, landing);
            return;
        }
        List<DeepDarkZoneState.Claim<BlockState>> claims = new ArrayList<>(targets.size());
        for (DeepDarkZoneShape.Target target : targets) {
            BlockPos pos = target.pos();
            claims.add(new DeepDarkZoneState.Claim<>(pos.asLong(), DeepDarkZoneBlocks.fakeState(world, pos),
                    world.getBlockState(pos), now, now + DeepDarkZoneSchedule.restoreOffset(target.distance())));
        }
        Runtime runtime = RUNTIMES.computeIfAbsent(world, ignored -> new Runtime(now));
        LongList stale = runtime.state.addZone(owner, landing.asLong(), now,
                now + DeepDarkZoneSchedule.lifetimeTicks(), claims);
        DeepDarkZoneSync.sendReal(world, stale);
        DeepDarkZoneCues.landing(world, landing);
    }

    private static void tickWorld(ServerWorld world) {
        Runtime runtime = RUNTIMES.get(world);
        if (runtime == null) {
            return;
        }
        // Also covers /stop and modes without a win event: zones live only while the round is ACTIVE.
        // 同时覆盖 /stop 与没有胜负事件的模式：领域只在对局 ACTIVE 时存在。
        if (!isRoundActive(world)) {
            clear(world);
            return;
        }
        long now = world.getTime();
        LongFunction<BlockState> realState = pos -> realStateIfLoaded(world, pos);
        DeepDarkZoneState.Changes changes = runtime.state.advance(now, realState);
        DeepDarkZoneSync.sendFake(world, changes.shown(), runtime.state::fakeAt);
        DeepDarkZoneSync.sendReal(world, changes.hidden());
        DeepDarkZoneCues.spread(world, changes.shown());
        DeepDarkZoneCues.restore(world, changes.hidden());
        DeepDarkZoneCues.hold(world, runtime.state.zones(), now);
        if (now >= runtime.nextHealTick) {
            runtime.nextHealTick = now + HEAL_INTERVAL_TICKS;
            DeepDarkZoneState.Heal heal = runtime.state.heal(realState);
            DeepDarkZoneSync.sendReal(world, heal.dropped());
            DeepDarkZoneSync.sendFake(world, heal.resend(), runtime.state::fakeAt);
        }
        if (runtime.state.isEmpty()) {
            RUNTIMES.remove(world);
        }
    }

    private static void clearWorld(World world) {
        if (world instanceof ServerWorld serverWorld) {
            clear(serverWorld);
        }
    }

    /**
     * Instant restore: real states go back to every tracker, the registry is dropped, and the world's players lose
     * their exposure in the same tick (zero synced to each owner), so the boosted drain and the pseudo task end with
     * the blocks.
     * 立即恢复：真实状态发回所有追踪者，丢弃登记表，并在同一刻清除该世界玩家的暴露（向各拥有者同步零值），
     * 使加速的理智下降与临时任务随方块一同结束。
     */
    private static void clear(ServerWorld world) {
        Runtime runtime = RUNTIMES.remove(world);
        if (runtime != null) {
            DeepDarkZoneSync.sendReal(world, runtime.state.clear());
        }
        DeepDarkZoneStandingService.clearAllExposure(world);
    }

    private static boolean isRoundActive(ServerWorld world) {
        return GameWorldComponent.KEY.get(world).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
    }

    /** Null for an unloaded chunk: unknown, so the cell is kept rather than force-loading. / 未加载区块返回 null。 */
    private static @Nullable BlockState realStateIfLoaded(ServerWorld world, long pos) {
        BlockPos blockPos = BlockPos.fromLong(pos);
        return DeepDarkZoneBlocks.isLoaded(world, blockPos) ? world.getBlockState(blockPos) : null;
    }

    private static final class Runtime {
        private final DeepDarkZoneState<BlockState> state = new DeepDarkZoneState<>();
        private long nextHealTick;

        private Runtime(long now) {
            this.nextHealTick = now + HEAL_INTERVAL_TICKS;
        }
    }
}
