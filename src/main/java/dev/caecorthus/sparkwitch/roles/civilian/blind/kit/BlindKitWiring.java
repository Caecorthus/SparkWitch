package dev.caecorthus.sparkwitch.roles.civilian.blind.kit;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.civilian.blind.BlindParticipants;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorTraitsBridge;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import java.util.List;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.DecoratedPotBlock;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;

/**
 * Server wiring of the Blind's kit, economy, shop and lifecycle. Round start grants in an own
 * {@code ON_FINISH_INITIALIZE} phase after Wathe's default phase, where SparkTraits' Conscience compensation has settled
 * final roles and Wathe has started the match record (the id bound here). The kit and the {@code sparkwitch:blind}
 * state are stripped on role loss, terminal death (not a SparkTraits-intercepted one), reset, finalize and disconnect,
 * and by a staggered sweep for anyone who is not a living, playing, exact Blind.
 * 盲人道具、经济、商店与生命周期的服务端接线。开局发放位于 Wathe 默认阶段之后的自有 {@code ON_FINISH_INITIALIZE}
 * 阶段：此时 SparkTraits 良知补偿已确定最终身份，Wathe 也已开始对局记录（此处绑定其 id）。失去职业、终结死亡
 * （非 SparkTraits 拦截的死亡）、重置、局末与断线时清除道具与 {@code sparkwitch:blind} 状态，并对所有不是存活、
 * 在局的精确盲人的玩家错峰清理。
 */
public final class BlindKitWiring {
    static final Identifier FINISH_INITIALIZE_PHASE = SparkWitch.id("blind_finish_initialize");
    private static boolean registered;

    private BlindKitWiring() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        BlindSounds.register();
        BlindShopService.register();
        BlindEconomyService.register();
        GameEvents.ON_FINISH_INITIALIZE.addPhaseOrdering(Event.DEFAULT_PHASE, FINISH_INITIALIZE_PHASE);
        GameEvents.ON_FINISH_INITIALIZE.register(FINISH_INITIALIZE_PHASE, (world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                BlindLoadoutService.grantRoundStart(serverWorld, game);
            }
        });
        // Grand Witch recruitment fires RoleAssigned before it restores only its retained items (keys, master keys, the
        // revolver and letters), so nothing of the kit survives on an Accomplice.
        // 大魔女招募先触发 RoleAssigned，再只恢复其保留物品（钥匙、万能钥匙、左轮与信件），因此共犯身上不会留下道具。
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                BlindLoadoutService.onRoleAssigned(serverPlayer, role);
            }
        });
        // A SparkTraits-intercepted death keeps the player in play, so the kit stays. / 被 SparkTraits 拦截的死亡仍在局，保留道具。
        KillPlayer.AFTER.register((victim, killer, reason) -> {
            if (victim != null && !WitchFactorTraitsBridge.isDeathIntercepted(victim)) {
                BlindLoadoutService.strip(victim);
            }
        });
        ResetPlayer.EVENT.register(BlindLoadoutService::strip);
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                MinecraftServer server = serverWorld.getServer();
                for (ServerPlayerEntity player : List.copyOf(server.getPlayerManager().getPlayerList())) {
                    BlindLoadoutService.strip(player);
                }
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (handler.player != null) {
                BlindLoadoutService.strip(handler.player);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(BlindKitWiring::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BlindCaneService.forgetAll());
        // Item frames, armor stands and decorated pots would take a held bound item out of the inventory; every other
        // block or entity use (doors included) stays untouched.
        // 物品展示框、盔甲架与饰纹陶罐会拿走手持的绑定物品；其他方块或实体交互（包括门）不受影响。
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                (entity instanceof ItemFrameEntity || entity instanceof ArmorStandEntity)
                        && BlindInventoryRules.isBound(player.getStackInHand(hand))
                        ? ActionResult.FAIL : ActionResult.PASS);
        UseBlockCallback.EVENT.register((player, world, hand, hit) ->
                BlindInventoryRules.isBound(player.getStackInHand(hand))
                        && world.getBlockState(hit.getBlockPos()).getBlock() instanceof DecoratedPotBlock
                        ? ActionResult.FAIL : ActionResult.PASS);
    }

    /**
     * Every server tick: an active Blind gets its upkeep (grant self-heal, one cane in the hotbar, cane re-scan); anyone
     * else is swept on a staggered 20-tick cadence unless they are a real Blind inside a SparkTraits-intercepted (Last
     * Stand) death. Cheap when idle: one role check per player.
     * 每个服务端 tick：激活的盲人执行维护（发放自愈、快捷栏一根盲杖、盲杖补扫）；其他人按错开的 20 刻节奏清理，
     * 处于 SparkTraits 拦截（背水一战）死亡中的真实盲人除外。空闲时开销很低：每名玩家一次职业判断。
     */
    private static void tick(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (BlindParticipants.isActiveBlind(player)) {
                BlindLoadoutService.tickHolder(player);
                BlindCaneService.tickHolder(player);
            } else if (BlindKitRules.straySweepDue(player.getServerWorld().getTime(), player.getId())
                    && !BlindLoadoutService.keepsKit(player)) {
                BlindLoadoutService.strip(player);
            }
        }
    }
}
