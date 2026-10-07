package dev.caecorthus.sparkwitch.roles.witch.potiongunner;

import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.PotionShellEntity;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.caecorthus.sparkwitch.util.OffMatchUse;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;

/**
 * Bound-item lifecycle: strips the launcher and shells from anyone who is not a living Potion Gunner (role change,
 * terminal death, reset, finalize, staggered sweep), and the same sweep re-grants a launcher to a living gunner who
 * has none (covering any inventory rewrite). The sweep binds only match participants
 * ({@link OffMatchUse#isMatchParticipant}, owner rule 2026-10-04): a free holder's copies are never stripped, granted,
 * deduplicated or surfaced. Match shells still in flight are discarded by the shell entity itself once the round
 * stops; finalize discards every shell. Because the sweep re-grants, a bound item must never be handed to a world
 * target that keeps it (the entity-use veto here, the decorated-pot mixin). Also registers the fire replay formatter.
 * 绑定物品生命周期：从任何不是存活药炮手的玩家身上收走炮筒与炮弹（换职业、最终死亡、重置、收尾、错峰清扫），同一次
 * 清扫也会给没有炮筒的存活药炮手补发（覆盖任何对背包的重写）。清扫只约束对局参与者（{@link OffMatchUse#isMatchParticipant}，
 * 所有者规则 2026-10-04）：自由持有者的物品从不被收走、补发、去重或移出。仍在飞行的对局炮弹在对局停止后由炮弹实体自行
 * 移除；收尾清理移除所有炮弹。由于清扫会补发，绑定物品绝不能交给会留下它的世界目标（此处的实体交互否决与饰纹陶罐 mixin）。
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
        // The launcher and shells cannot be handed to item frames, armor stands or allays. Both sides: the client stops
        // before sending, the server refuses a forged packet. Decorated pots are handled by
        // mixin/potiongunner/DecoratedPotBlockPotionGunnerItemMixin instead, so the launcher's own use still runs
        // there.
        // 炮筒与炮弹无法交给物品展示框、盔甲架或悦灵。双端生效：客户端在发包前拦截，服务端拒绝伪造的数据包。
        // 饰纹陶罐改由 DecoratedPotBlockPotionGunnerItemMixin 处理，因此在陶罐前炮筒自身的使用照常进行。
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                PotionGunnerInventoryRules.blocksEntityUse(player.getStackInHand(hand), entity)
                        ? ActionResult.FAIL : ActionResult.PASS);
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
            if (!isSweepTick(time, player.getId()) || !OffMatchUse.isMatchParticipant(player)) {
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
