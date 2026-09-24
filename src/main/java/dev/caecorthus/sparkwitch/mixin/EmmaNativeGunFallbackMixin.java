package dev.caecorthus.sparkwitch.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.civilian.emma.EmmaCooldowns;
import dev.caecorthus.sparkwitch.roles.civilian.emma.EmmaGunService;
import dev.doctor4t.wathe.util.GunShootPayload;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Loaded only without Traits, so the provider's redirected target invocation stays untouched.
 * 仅在未安装 Traits 时加载，避免触碰提供方重定向后的目标调用。 */
@Mixin(value = GunShootPayload.Receiver.class, remap = false)
public abstract class EmmaNativeGunFallbackMixin {
    @Unique private static final ThreadLocal<UUID> SPARKWITCH_EMMA_CYCLE = new ThreadLocal<>();

    @WrapMethod(method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V")
    private void sparkwitch$closeNativeCycle(GunShootPayload payload, ServerPlayNetworking.Context context,
                                            Operation<Void> original) {
        UUID previous = SPARKWITCH_EMMA_CYCLE.get();
        SPARKWITCH_EMMA_CYCLE.remove();
        try {
            original.call(payload, context);
        } finally {
            UUID cycle = SPARKWITCH_EMMA_CYCLE.get();
            if (cycle != null) EmmaGunService.cycleClosed(context.player(), cycle);
            if (previous == null) SPARKWITCH_EMMA_CYCLE.remove(); else SPARKWITCH_EMMA_CYCLE.set(previous);
        }
    }

    @WrapOperation(method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE", target = "Ldev/doctor4t/wathe/game/GameFunctions;killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;)V", ordinal = 1))
    private void sparkwitch$observeNativeKill(ServerPlayerEntity target, boolean body, ServerPlayerEntity shooter,
                                             Identifier reason, Operation<Void> original) {
        UUID cycle = sparkwitch$cycle(shooter);
        Object token = cycle == null ? null : EmmaGunService.beforeTargetKill(shooter, cycle, target);
        original.call(target, body, shooter, reason);
        if (cycle != null) EmmaGunService.afterTargetKill(shooter, cycle, target, token);
    }

    @WrapOperation(method = "receive(Ldev/doctor4t/wathe/util/GunShootPayload;Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/ItemCooldownManager;set(Lnet/minecraft/item/Item;I)V", remap = true))
    private void sparkwitch$nativeCooldown(ItemCooldownManager manager, Item item, int duration, Operation<Void> original,
                                           GunShootPayload payload, ServerPlayNetworking.Context context) {
        UUID cycle = sparkwitch$cycle(context.player());
        original.call(manager, item, duration);
        if (cycle != null) EmmaGunService.initialCooldownEstablished(context.player(), cycle, item,
                EmmaCooldowns.start(context.player(), item), EmmaCooldowns.end(context.player(), item),
                EmmaCooldowns.tick(context.player()));
    }

    @Unique private static UUID sparkwitch$cycle(ServerPlayerEntity shooter) {
        if (!EmmaGunService.eligible(shooter)) return null;
        UUID cycle = SPARKWITCH_EMMA_CYCLE.get();
        if (cycle == null) {
            cycle = UUID.randomUUID();
            SPARKWITCH_EMMA_CYCLE.set(cycle);
            EmmaGunService.cycleStarted(shooter, cycle, shooter.getMainHandStack().getItem());
        }
        return cycle;
    }
}
