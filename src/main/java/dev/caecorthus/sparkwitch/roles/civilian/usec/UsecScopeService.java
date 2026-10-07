package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.caecorthus.sparkwitch.roles.civilian.usec.net.UsecScopeC2SPacket;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;

/**
 * Server authority for {@code UsecPlayerComponent.scoped}, the all-player flag behind the scope glint (S1). The
 * {@code sparkwitch:usec_scope} receiver stores {@code scoped = true} only for a qualifying shooter: a playing, alive,
 * non-spectating, non-Wraith player whose exact role is USEC, holding the rifle in the main hand and using it (the
 * held use that {@code UsecRifleItem.use} starts); anything else stores false. Every server tick the scoped players
 * are re-checked and cleared once they stop qualifying (released use, swapped item, death, role or round change), so a
 * lost "false" packet can never leave a stale glint. The payload is harmless presentation and is deliberately on no
 * deny-list. Fabric runs play receivers on the server thread, so no hand-off is needed.
 * {@code UsecPlayerComponent.scoped}（镜头反光 S1 背后的全员同步标记）的服务端权威。{@code sparkwitch:usec_scope} 接收器
 * 只为符合条件的射手写入 {@code scoped = true}：在局、存活、非旁观、非冤魂、真实职业为 USEC、主手持狙击步枪并正在使用
 * （即 {@code UsecRifleItem.use} 开始的按住使用）；其他情况一律写入 false。每个服务端刻都会复查已开镜的玩家，一旦不再
 * 符合条件（松开使用、换物品、死亡、职业或对局变化）即清除，因此丢失的 "false" 数据包永远不会留下过期反光。该数据包是
 * 无害的表现信息，刻意不进入任何拒绝列表。Fabric 在服务端线程执行游戏数据包接收器，因此无需切换线程。
 */
public final class UsecScopeService {
    private static boolean registered;

    private UsecScopeService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerPlayNetworking.registerGlobalReceiver(UsecScopeC2SPacket.ID,
                (payload, context) -> receive(context.player(), payload.scoped()));
        ServerTickEvents.END_SERVER_TICK.register(UsecScopeService::sweep);
    }

    static void receive(ServerPlayerEntity player, boolean scoped) {
        UsecPlayerComponent component = component(player);
        if (component != null) {
            component.setScoped(scoped && qualifies(player));
        }
    }

    /** Clears every scoped player who no longer qualifies; unscoped players cost one flag read. / 清除不再符合条件者。 */
    static void sweep(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UsecPlayerComponent component = component(player);
            if (component != null && component.isScoped() && !qualifies(player)) {
                component.setScoped(false);
            }
        }
    }

    /** The live server facts behind {@link #qualifies(boolean, boolean, boolean, boolean, boolean, boolean)}. */
    public static boolean qualifies(ServerPlayerEntity player) {
        if (player == null || player.isDisconnected()) {
            return false;
        }
        ItemStack mainHand = player.getMainHandStack();
        boolean rifleInMainHand = mainHand.getItem() instanceof UsecRifleItem;
        boolean usingRifle = rifleInMainHand && player.isUsingItem() && player.getActiveHand() == Hand.MAIN_HAND
                && player.getActiveItem().getItem() instanceof UsecRifleItem;
        return qualifies(
                GameFunctions.isPlayerPlayingAndAlive(player),
                player.isSpectator(),
                WraithStateService.isActive(player),
                UsecRules.isUsec(GameWorldComponent.KEY.get(player.getWorld()).getRole(player)),
                rifleInMainHand,
                usingRifle);
    }

    /**
     * Pure rule: who may show a scope glint. / 纯规则：谁可以显示镜头反光。
     */
    public static boolean qualifies(boolean playingAndAlive, boolean spectator, boolean wraithActive, boolean usecRole,
                                    boolean rifleInMainHand, boolean usingRifleInMainHand) {
        return playingAndAlive && !spectator && !wraithActive && usecRole && rifleInMainHand && usingRifleInMainHand;
    }

    private static UsecPlayerComponent component(ServerPlayerEntity player) {
        return player == null ? null : UsecPlayerComponent.KEY.getNullable(player);
    }
}
