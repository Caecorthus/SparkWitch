package dev.caecorthus.sparkwitch.mixin.hanna;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.roles.civilian.hanna.HannaSoundScope;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * Hanna float silence, scope seam 2 of 4: the two vanilla packets that resolve a player's own action outside
 * {@code ServerPlayerInteractionManager}.
 * <ul>
 *   <li>{@code onPlayerInteractEntity}: attacks and right-clicks on entities (vanilla's attack sounds are sourceless,
 *   and other mods resolve their entity interactions inside this handler).</li>
 *   <li>{@code onPlayerAction}: {@code RELEASE_USE_ITEM} runs {@code Item#onStoppedUsing} (the Throwing Axe and
 *   shuriken throws, the knife and sword releases).</li>
 * </ul>
 * {@code onHandSwing} is left out: it only relays the swing animation and plays nothing.
 * <p>Both handlers are first called on the Netty thread, where {@code NetworkThreadUtils.forceMainThread} throws and
 * reschedules them. The scope is entered only on the server thread, so the first call does nothing here; the handler
 * always runs exactly once per call with its original packet.
 * 汉娜浮游静音，作用域接缝 2/4：在 {@code ServerPlayerInteractionManager} 之外结算玩家自身行动的两个原版数据包。
 * {@code onPlayerInteractEntity}：对实体的攻击与右键（原版攻击声无来源，其他模组也在此处理器内结算实体交互）。
 * {@code onPlayerAction}：{@code RELEASE_USE_ITEM} 会执行 {@code Item#onStoppedUsing}（投掷斧与手里剑的投掷、刀剑的松开）。
 * 不包含 {@code onHandSwing}：它只转发挥手动画，不播放任何声音。
 * <p>两个处理器都先在 Netty 线程被调用，{@code NetworkThreadUtils.forceMainThread} 在那里抛出并重新调度。作用域只在服务端
 * 线程进入，因此首次调用在这里什么都不做；处理器每次调用始终以原数据包恰好执行一次。
 */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerHannaSoundScopeMixin {
    @Shadow
    public ServerPlayerEntity player;

    @WrapMethod(method = "onPlayerInteractEntity")
    private void sparkwitch$scopeHannaEntityInteractionSounds(PlayerInteractEntityC2SPacket packet,
                                                              Operation<Void> original) {
        if (!sparkwitch$hannaOnServerThread()) {
            original.call(packet);
            return;
        }
        ServerPlayerEntity previous = HannaSoundScope.enter(player);
        try {
            original.call(packet);
        } finally {
            HannaSoundScope.exit(previous);
        }
    }

    @WrapMethod(method = "onPlayerAction")
    private void sparkwitch$scopeHannaPlayerActionSounds(PlayerActionC2SPacket packet, Operation<Void> original) {
        if (!sparkwitch$hannaOnServerThread()) {
            original.call(packet);
            return;
        }
        ServerPlayerEntity previous = HannaSoundScope.enter(player);
        try {
            original.call(packet);
        } finally {
            HannaSoundScope.exit(previous);
        }
    }

    @Unique
    private boolean sparkwitch$hannaOnServerThread() {
        ServerPlayerEntity current = player;
        return current != null && current.server != null && current.server.isOnThread();
    }
}
