package dev.caecorthus.sparkwitch.roles.civilian.discoverer;

import dev.doctor4t.wathe.Wathe;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerMoodComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * 探索者 passives: open-air Speed II and the per-task payout. The role has no passive income.
 * 探索者的被动：露天的速度 II 与按任务结算的金币；该身份没有被动收入。
 */
public final class DiscovererService {

    private DiscovererService() {
    }

    public static void register() {
        TaskComplete.EVENT.register(DiscovererService::onTaskComplete);
        ServerTickEvents.END_SERVER_TICK.register(DiscovererService::tick);
    }

    /**
     * +50 coins per finished task, every task type included — the owner confirmed the stray income is not the
     * open-air task, so it stays payable.
     * 每完成一个任务 +50 金币，所有任务类型都算——所有者已确认多出来的金币不是露天任务造成的，因此保留它的报酬。
     */
    private static void onTaskComplete(ServerPlayerEntity player, PlayerMoodComponent.Task taskType) {
        if (!isDiscoverer(player)) {
            return;
        }
        PlayerShopComponent shop = PlayerShopComponent.KEY.get(player);
        shop.setBalance(shop.getBalance() + DiscovererRules.TASK_REWARD);
    }

    private static void tick(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            for (ServerPlayerEntity player : world.getPlayers()) {
                if (!isDiscoverer(player)) {
                    continue;
                }
                // Wathe's own open-air test: eye position, four diagonals, and not standing inside a door leaf.
                // Wathe 自己的露天判定：眼睛位置、四个斜角、且人不在门扇方块里。
                if (Wathe.isSkyVisibleAdjacent(player)) {
                    applyOpenAirSpeed(player);
                }
            }
        }
    }

    /**
     * Hands out Speed II with a duration that outlives the next check, so the icon stays steady while the condition
     * holds and simply expires a moment after the player steps back inside — no active removal, so another source's
     * Speed is never stripped.
     * 发放速度 II，时长比重检间隔长：条件成立时图标稳定，回到室内后效果自然过期；
     * 不需要主动移除，因此不会顶掉别的来源给的速度。
     */
    private static void applyOpenAirSpeed(ServerPlayerEntity player) {
        StatusEffectInstance current = player.getStatusEffect(StatusEffects.SPEED);
        boolean strongEnough = current != null && current.getAmplifier() >= DiscovererRules.OPEN_AIR_SPEED_AMPLIFIER;
        if (strongEnough && current.getDuration() > DiscovererRules.OPEN_AIR_SPEED_REFRESH_BELOW_TICKS) {
            return;
        }
        player.addStatusEffect(new StatusEffectInstance(
                StatusEffects.SPEED,
                DiscovererRules.OPEN_AIR_SPEED_DURATION_TICKS,
                DiscovererRules.OPEN_AIR_SPEED_AMPLIFIER,
                false,
                false,
                true));
    }

    public static boolean isDiscoverer(ServerPlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return false;
        }
        var role = GameWorldComponent.KEY.get(world).getRole(player);
        return role != null && DiscovererRules.ROLE_ID.equals(role.identifier());
    }
}
