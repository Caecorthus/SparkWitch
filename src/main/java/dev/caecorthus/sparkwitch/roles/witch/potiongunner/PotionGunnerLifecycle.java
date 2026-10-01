package dev.caecorthus.sparkwitch.roles.witch.potiongunner;

import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.PotionShellEntity;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Bound-item lifecycle: strips the launcher and shells from anyone who is not a living Potion Gunner (role change,
 * terminal death, reset, finalize, staggered sweep), and the same sweep re-grants a launcher to a living gunner who
 * has none (covering recruitment's inventory rewrite). Shells still in flight are discarded by the shell entity itself
 * once the round stops. Also registers the fire replay formatter.
 * 绑定物品生命周期：从任何不是存活药炮手的玩家身上收走炮筒与炮弹（换职业、最终死亡、重置、收尾、错峰清扫），同一次
 * 清扫也会给没有炮筒的存活药炮手补发（覆盖招募对背包的重写）。仍在飞行的炮弹在对局停止后由炮弹实体自行移除。
 * 同时注册发射回放格式化器。
 */
public final class PotionGunnerLifecycle {
    /** Cadence of the staggered bound-item sweep. / 绑定物品错峰清扫的间隔。 */
    static final int SWEEP_INTERVAL_TICKS = 20;

    private static boolean registered;

    private PotionGunnerLifecycle() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PotionGunnerReplay.register();
        // Role change away from the gunner strips at once; granting a new gunner is the feature service's listener.
        // 换成其他职业时立即收走；给新药炮手发放由功能服务的监听器负责。
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer && !PotionGunnerRules.isPotionGunner(role)) {
                PotionGunnerLoadoutService.stripAll(serverPlayer);
            }
        });
        // A SparkTraits-intercepted death (Last Stand) leaves the player in play, so nothing is stripped.
        // 被 SparkTraits 拦截的死亡（最后的坚守）使玩家仍留在对局中，因此不收走任何物品。
        KillPlayer.AFTER.register((victim, killer, deathReason) -> {
            if (WitchFactorTraitsBridge.isDeathIntercepted(victim)) {
                return;
            }
            PotionGunnerLoadoutService.stripAll(victim);
        });
        ResetPlayer.EVENT.register(PotionGunnerLoadoutService::stripAll);
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (!(world instanceof ServerWorld serverWorld)) {
                return;
            }
            for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                PotionGunnerLoadoutService.stripAll(player);
            }
            // Shells still in flight never outlive the round. / 仍在飞行的炮弹不会延续到对局之外。
            PotionShellEntity.discardAll(serverWorld);
        });
        ServerTickEvents.END_WORLD_TICK.register(PotionGunnerLifecycle::sweep);
    }

    /** Each player is visited once per interval, offset by entity id. / 每名玩家每个间隔按实体 id 错开访问一次。 */
    static boolean isSweepTick(long worldTime, int entityId) {
        return Math.floorMod(worldTime + entityId, SWEEP_INTERVAL_TICKS) == 0;
    }

    private static void sweep(ServerWorld world) {
        long time = world.getTime();
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!isSweepTick(time, player.getId())) {
                continue;
            }
            if (PotionGunnerLoadoutService.mayHold(player)) {
                PotionGunnerLoadoutService.ensureLauncher(player);
            } else {
                PotionGunnerLoadoutService.stripAll(player);
            }
        }
    }
}
