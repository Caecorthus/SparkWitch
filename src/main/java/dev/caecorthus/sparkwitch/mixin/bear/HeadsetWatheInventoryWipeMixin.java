package dev.caecorthus.sparkwitch.mixin.bear;

import dev.caecorthus.sparkwitch.headset.HeadsetLifecycle;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wathe's join-time wipe: {@code GameFunctions.teleportPlayer} (called from its JOIN handler for a player who joins
 * while no round runs and stands in another dimension than the selected map, or who joins a running round as a
 * spectator or creative player, and from {@code finalizeVoting}) moves the player to the map spawn 100 ms later on the
 * server thread and then empties {@code PlayerInventory}. The headset slot lives outside {@code PlayerInventory}, so
 * right after that very {@code clear()} it is emptied too: same condition, same moment, nothing re-derived. A living
 * survival player who reconnects into a running round never reaches {@code teleportPlayer} and keeps inventory and
 * headset alike. The pinned Wathe jar names the server-thread lambda {@code lambda$teleportPlayer$1}; a rebuilt jar
 * that moves it fails at load ({@code require = 1}) instead of silently losing the wipe. Server only.
 * Wathe 的进服清空：{@code GameFunctions.teleportPlayer}（其 JOIN 处理在“没有对局且玩家不在所选地图维度”或“对局进行中且玩家
 * 为旁观/创造模式”时调用，{@code finalizeVoting} 也会调用）在 100 毫秒后于服务端线程把玩家传送到地图出生点，然后清空
 * {@code PlayerInventory}。耳机槽不在 {@code PlayerInventory} 内，因此紧跟在同一次 {@code clear()} 之后一并清空：条件相同、
 * 时机相同，不另行推导。重连进入进行中对局的存活生存玩家不会进入 {@code teleportPlayer}，背包与耳机槽都保留。固定版本的
 * Wathe jar 中服务端线程 lambda 名为 {@code lambda$teleportPlayer$1}；重编后若移动，将在加载时报错（{@code require = 1}），
 * 而不是悄悄失去清空。仅服务端。
 */
@Mixin(GameFunctions.class)
public abstract class HeadsetWatheInventoryWipeMixin {
    @Inject(
            method = "lambda$teleportPlayer$1(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/server/MinecraftServer;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerInventory;clear()V",
                    shift = At.Shift.AFTER),
            require = 1,
            allow = 1
    )
    private static void sparkwitch$clearHeadsetWithInventory(ServerPlayerEntity player, MinecraftServer server,
                                                             CallbackInfo ci) {
        HeadsetLifecycle.onWatheInventoryWipe(player);
    }
}
