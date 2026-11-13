package dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither;

import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.WitchMaidenRules;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.net.FireCrimsonSkullC2SPayload;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.net.ToggleMiniWitherC2SPayload;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.caecorthus.sparkwitch.util.OffMatchUse;
import dev.caecorthus.sparkwitch.util.StaggeredSweep;
import dev.caecorthus.sparkwitch.util.ServerDisconnectCleanup;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

/**
 * Event wiring for the Mini Wither: both payloads, the two-task unlock, the Wither Gohei's bound-item lifecycle and
 * round cleanup. Runs from the common initializer, so the payload types exist on both sides; only the server acts.
 * 小凋灵的事件接线：两个数据包、两个任务解锁、凋灵御币的绑定物品生命周期以及回合清理。在通用初始化器中运行，
 * 因此数据包类型在两端都存在；只有服务端执行。
 */
public final class MiniWitherFeatureService {
    /** Cadence of the staggered Gohei sweep. / 御币错峰清扫的间隔。 */
    static final int SWEEP_INTERVAL_TICKS = 20;

    private static boolean registered;

    private MiniWitherFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PayloadTypeRegistry.playC2S().register(ToggleMiniWitherC2SPayload.ID, ToggleMiniWitherC2SPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(FireCrimsonSkullC2SPayload.ID, FireCrimsonSkullC2SPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(ToggleMiniWitherC2SPayload.ID,
                (payload, context) -> MiniWitherSkillService.toggle(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(FireCrimsonSkullC2SPayload.ID,
                (payload, context) -> CrimsonSkullFireService.fire(context.player(), payload));
        MiniWitherSkillService.registerReplayFormatter();
        TaskComplete.EVENT.register((player, taskType) -> onTaskComplete(player));
        // Any new role starts over: progress, Wither and Gohei. / 任何新职业都从头开始：进度、小凋灵与御币。
        RoleAssigned.EVENT.register((player, role) -> {
            MiniWitherPlayerComponent.KEY.get(player).clear();
            if (player instanceof ServerPlayerEntity serverPlayer) {
                WitherGoheiLoadoutService.stripAll(serverPlayer);
            }
        });
        // A SparkTraits-intercepted death (Last Stand) leaves the player in play, so nothing is cleared.
        // 被 SparkTraits 拦截的死亡（最后的坚守）使玩家仍留在对局中，因此不清空任何东西。
        KillPlayer.AFTER.register((victim, killer, deathReason) -> {
            if (WitchFactorTraitsBridge.isDeathIntercepted(victim)) {
                return;
            }
            MiniWitherPlayerComponent.KEY.get(victim).clear();
            WitherGoheiLoadoutService.stripAll(victim);
        });
        ResetPlayer.EVENT.register(player -> {
            MiniWitherPlayerComponent.KEY.get(player).clear();
            WitherGoheiLoadoutService.stripAll(player);
        });
        ServerDisconnectCleanup.register((player, server) ->
                MiniWitherPlayerComponent.KEY.get(player).setActive(false));
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (!(world instanceof ServerWorld serverWorld)) {
                return;
            }
            for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                MiniWitherPlayerComponent.KEY.get(player).clear();
                WitherGoheiLoadoutService.stripAll(player);
            }
            MiniWitherSkullEntity.discardAll(serverWorld);
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) ->
                WitherGoheiInventoryRules.blocksEntityUse(player.getStackInHand(hand), entity)
                        ? ActionResult.FAIL : ActionResult.PASS);
        ServerTickEvents.END_WORLD_TICK.register(MiniWitherFeatureService::sweep);
    }

    /**
     * Counts the Witch Maiden's own completed tasks. Wathe is still iterating its task map during this event, and the
     * Gohei grant below is NOT deferred past that iteration: this event fires on the server thread, where
     * {@code MinecraftServer#execute} runs the task at once (it queues only from another thread). That is safe solely
     * because {@code ensureGohei} edits the inventory and the open screen handler and never the mood component's
     * tasks; anything that touches the task map must be queued for a later tick instead of being added to that block.
     * 统计巫女自己完成的任务。本事件期间 Wathe 仍在遍历其任务表，而下方的御币发放并未推迟到遍历之后：本事件在服务端线程触发，
     * 此时 {@code MinecraftServer#execute} 会立即执行任务（只有从其他线程调用才会排队）。之所以安全，仅仅是因为
     * {@code ensureGohei} 只修改物品栏与已打开的界面处理器，从不触碰心情组件的任务；任何会改动任务表的逻辑都必须排到之后的刻
     * 执行，而不能加进该代码块。
     */
    static void onTaskComplete(ServerPlayerEntity player) {
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getServerWorld());
        if (game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE
                || !GameFunctions.isPlayerPlayingAndAlive(player)
                || !WitchMaidenRules.isWitchMaiden(game.getRole(player))) {
            return;
        }
        if (!MiniWitherPlayerComponent.KEY.get(player).recordCompletedTask()) {
            return;
        }
        // A keybind text resolves to the owner's own binding of Role Skill 2 on their client.
        // 按键文本会在主人客户端上显示其自己绑定的职业技能 2 按键。
        player.sendMessage(Text.translatable("message.sparkwitch.mini_wither.unlocked",
                Text.keybind("key.sparkwitch.secondary_skill")).withColor(WitchMaidenRules.COLOR), false);
        player.playSoundToPlayer(SparkWitchSounds.miniWither(MiniWitherSoundSlot.UNLOCK), SoundCategory.PLAYERS, 0.5F,
                1.8F);
        player.getServer().execute(() -> {
            if (WitherGoheiLoadoutService.mayHold(player)) {
                WitherGoheiLoadoutService.ensureGohei(player);
            }
        });
    }

    /** Each player is visited once per interval, offset by entity id. / 每名玩家每个间隔按实体 id 错开访问一次。 */
    static boolean isSweepTick(long worldTime, int entityId) {
        return StaggeredSweep.isDue(worldTime, entityId, SWEEP_INTERVAL_TICKS);
    }

    private static void sweep(ServerWorld world) {
        long time = world.getTime();
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!isSweepTick(time, player.getId()) || !OffMatchUse.isMatchParticipant(player)) {
                continue;
            }
            if (WitherGoheiLoadoutService.mayHold(player)) {
                WitherGoheiLoadoutService.ensureGohei(player);
            } else {
                WitherGoheiLoadoutService.stripAll(player);
            }
        }
    }
}
