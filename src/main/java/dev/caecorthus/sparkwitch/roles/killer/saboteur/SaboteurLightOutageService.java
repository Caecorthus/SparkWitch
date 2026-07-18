package dev.caecorthus.sparkwitch.roles.killer.saboteur;

import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.cca.WorldBlackoutComponent;
import dev.doctor4t.wathe.index.WatheProperties;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Owns Sabotage's server-side, per-lamp outage leases. / 管理破坏技能服务端逐灯熄灭租约。 */
public final class SaboteurLightOutageService {
    public enum WatheEndAction {
        NATIVE,
        KEEP_DARK,
        RESTORE_AFTER_NATIVE;

        public boolean coordinatesLampRestoration() {
            return this != NATIVE;
        }
    }

    private static final Map<World, WorldRuntime> RUNTIMES = new IdentityHashMap<>();
    private static boolean registered;

    private SaboteurLightOutageService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;

        ServerTickEvents.END_WORLD_TICK.register(SaboteurLightOutageService::tickWorld);
        GameEvents.ON_FINISH_INITIALIZE.register((world, component) -> clearStaleWorld(world));
        GameEvents.ON_WIN_DETERMINED.register((world, component, status, neutralWinner) -> clearRound(world));
        GameEvents.ON_FINISH_FINALIZE.register((world, component) -> clearRound(world));
        ServerWorldEvents.UNLOAD.register((server, world) -> clearAll(world));
        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
                server.getWorlds().forEach(SaboteurLightOutageService::clearAll));
    }

    /** Activates successfully even when the scan finds no eligible lamps. / 即使范围内没有合格灯具，技能仍视为成功释放。 */
    public static void activate(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Vec3d center = player.getPos();
        int radius = SaboteurRules.LIGHT_RADIUS;
        long expiryTick = world.getTime() + SaboteurRules.LIGHT_DURATION_TICKS;
        BlockPos min = BlockPos.ofFloored(center.x - radius, center.y - radius, center.z - radius);
        BlockPos max = BlockPos.ofFloored(center.x + radius, center.y + radius, center.z + radius);

        for (BlockPos mutablePos : BlockPos.iterate(min, max)) {
            if (!SaboteurLightGeometry.containsBlockCenter(
                    center.x,
                    center.y,
                    center.z,
                    mutablePos.getX(),
                    mutablePos.getY(),
                    mutablePos.getZ(),
                    radius
            )) {
                continue;
            }
            BlockState state = world.getBlockState(mutablePos);
            if (!isWatheEligible(state)) {
                continue;
            }

            BlockPos pos = mutablePos.toImmutable();
            WorldRuntime runtime = RUNTIMES.computeIfAbsent(world, ignored -> new WorldRuntime());
            runtime.state.beginLocal(
                    pos,
                    expiryTick,
                    state.get(Properties.LIT),
                    state.get(WatheProperties.ACTIVE)
            );
            keepDark(world, pos);
        }
    }

    /**
     * Mirrors Wathe's per-lamp blackout source without borrowing its player effects or timer.
     * 镜像 Wathe 的逐灯熄灯来源，但不复用其玩家效果或全局计时。
     */
    public static void onWatheInit(
            World world,
            BlockPos pos,
            Object source,
            boolean watheOriginalLit
    ) {
        if (world.isClient()) {
            return;
        }
        BlockState current = world.getBlockState(pos);
        if (!isWatheEligible(current)) {
            return;
        }
        WorldRuntime runtime = RUNTIMES.computeIfAbsent(world, ignored -> new WorldRuntime());
        // Wathe does not persist ACTIVE and its native end always restores true; an earlier local snapshot still wins.
        // Wathe 不持久化 ACTIVE，且原生结束固定恢复为 true；若局部租约更早建立，仍保留局部原始值。
        runtime.state.beginWathe(
                pos.toImmutable(),
                source,
                watheOriginalLit,
                true
        );
    }

    /**
     * Ends one Wathe lamp source while keeping the combined local/global lease authoritative.
     * 结束一个 Wathe 灯光来源，同时由局部与全局联合租约决定是否恢复。
     */
    public static WatheEndAction onWatheEnd(World world, BlockPos pos, Object source) {
        WorldRuntime runtime = RUNTIMES.get(world);
        if (runtime == null) {
            return WatheEndAction.NATIVE;
        }
        SaboteurLightOutageState.WatheEndDecision decision = runtime.state.endWathe(pos, source);
        return switch (decision) {
            case NATIVE -> {
                removeRuntimeIfEmpty(world, runtime);
                yield WatheEndAction.NATIVE;
            }
            case KEEP_DARK -> {
                keepDark(world, pos);
                yield WatheEndAction.KEEP_DARK;
            }
            case RESTORE_AFTER_NATIVE -> WatheEndAction.RESTORE_AFTER_NATIVE;
        };
    }

    /** Applies the preserved pre-Sabotage state after Wathe performs its native final write. */
    public static void afterWatheEnd(World world, BlockPos pos) {
        WorldRuntime runtime = RUNTIMES.get(world);
        if (runtime == null) {
            return;
        }
        applyRestore(world, runtime.state.finishWatheEnd(pos));
        removeRuntimeIfEmpty(world, runtime);
    }

    /** Prevents Wathe's final flicker from lighting a lamp that still has a local lease. */
    public static BlockState protectWatheFlicker(World world, BlockPos pos, BlockState proposedState) {
        WorldRuntime runtime = RUNTIMES.get(world);
        if (runtime == null || !runtime.state.hasLocal(pos) || !isWatheEligible(proposedState)) {
            return proposedState;
        }
        return proposedState
                .with(Properties.LIT, false)
                .with(WatheProperties.ACTIVE, false);
    }

    static boolean isWatheEligible(BlockState state) {
        return state.contains(Properties.LIT)
                && state.contains(WatheProperties.ACTIVE);
    }

    private static void tickWorld(ServerWorld world) {
        WorldRuntime runtime = RUNTIMES.get(world);
        if (runtime == null) {
            return;
        }
        applyRestores(world, runtime.state.expireLocals(world.getTime()));
        removeRuntimeIfEmpty(world, runtime);
    }

    private static void clearRound(World world) {
        WorldRuntime runtime = RUNTIMES.get(world);
        if (runtime == null) {
            return;
        }
        applyRestores(world, runtime.state.clearLocals());
        removeRuntimeIfEmpty(world, runtime);
    }

    private static void clearStaleWorld(World world) {
        if (world instanceof ServerWorld serverWorld) {
            clearAll(serverWorld);
        }
    }

    private static void clearAll(ServerWorld world) {
        WorldRuntime runtime = RUNTIMES.get(world);
        if (runtime == null) {
            return;
        }
        // A local snapshot cannot survive an unload. End only an actually entangled Wathe outage first,
        // so its persisted `original=false` value cannot later strand a formerly lit lamp in darkness.
        // 局部快照无法跨卸载保存；仅先结束真正发生重叠的 Wathe 熄灯，避免其持久化的
        // `original=false` 在重载后让原本点亮的灯永久保持熄灭。
        if (runtime.state.hasLocalWatheOverlap()) {
            WorldBlackoutComponent.KEY.get(world).reset();
        }
        RUNTIMES.remove(world);
        applyRestores(world, runtime.state.clearAll());
    }

    private static void keepDark(World world, BlockPos pos) {
        BlockState current = world.getBlockState(pos);
        if (!isWatheEligible(current)) {
            return;
        }
        BlockState dark = current
                .with(Properties.LIT, false)
                .with(WatheProperties.ACTIVE, false);
        if (dark != current) {
            world.setBlockState(pos, dark);
        }
    }

    private static void applyRestores(World world, List<SaboteurLightOutageState.Restore<BlockPos>> restores) {
        for (SaboteurLightOutageState.Restore<BlockPos> restore : restores) {
            applyRestore(world, restore);
        }
    }

    private static void applyRestore(World world, SaboteurLightOutageState.Restore<BlockPos> restore) {
        if (restore == null) {
            return;
        }
        BlockState current = world.getBlockState(restore.key());
        if (!isWatheEligible(current)) {
            return;
        }
        BlockState restored = current
                .with(Properties.LIT, restore.lit())
                .with(WatheProperties.ACTIVE, restore.active());
        if (restored != current) {
            world.setBlockState(restore.key(), restored);
        }
    }

    private static void removeRuntimeIfEmpty(World world, WorldRuntime runtime) {
        if (runtime.state.isEmpty()) {
            RUNTIMES.remove(world);
        }
    }

    private static final class WorldRuntime {
        private final SaboteurLightOutageState<BlockPos> state = new SaboteurLightOutageState<>();
    }
}
