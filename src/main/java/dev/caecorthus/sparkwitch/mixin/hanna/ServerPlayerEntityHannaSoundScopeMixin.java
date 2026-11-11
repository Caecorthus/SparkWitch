package dev.caecorthus.sparkwitch.mixin.hanna;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.roles.civilian.hanna.HannaSoundScope;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Hanna float silence, scope seam 3 of 4: the player's own tick. An item finishes being used here, not in a packet
 * handler ({@code LivingEntity#tickActiveItemStack} to {@code consumeItem} to {@code Item#finishUsing}), and vanilla
 * plays the finishing sounds without a source ({@code PlayerEntity#eatFood}'s burp, {@code LivingEntity#eatFood}'s
 * eat sound), as do item {@code usageTick} / {@code inventoryTick} sounds. {@code playerTick} is called from
 * {@code ServerPlayNetworkHandler#tick} on the server thread only; it always runs exactly once, with only the
 * thread-local actor set around it. Component ticks and other players' ticks are outside this scope.
 * 汉娜浮游静音，作用域接缝 3/4：玩家自身的 tick。物品的使用在这里完成，而不是在数据包处理器中
 * （{@code LivingEntity#tickActiveItemStack} → {@code consumeItem} → {@code Item#finishUsing}），原版以无来源方式播放完成时的
 * 声音（{@code PlayerEntity#eatFood} 的打嗝声、{@code LivingEntity#eatFood} 的进食声），物品的 {@code usageTick} /
 * {@code inventoryTick} 声音也是如此。{@code playerTick} 只由 {@code ServerPlayNetworkHandler#tick} 在服务端线程调用；
 * 它始终恰好执行一次，仅在其前后设置线程局部的行动者。组件 tick 与其他玩家的 tick 不在此作用域内。
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityHannaSoundScopeMixin {
    @WrapMethod(method = "playerTick")
    private void sparkwitch$scopeHannaOwnTickSounds(Operation<Void> original) {
        ServerPlayerEntity previous = HannaSoundScope.enter((ServerPlayerEntity) (Object) this);
        try {
            original.call();
        } finally {
            HannaSoundScope.exit(previous);
        }
    }
}
