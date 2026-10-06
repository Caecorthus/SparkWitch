package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.special.wraith.WraithParticipationRules;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.explosion.Explosion;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stops wind charges thrown by an active Wraith (the promoted Wind Spirit's shop charge) from toggling doors,
 * trapdoors, fence gates, buttons, levers, bells and candles. Every vanilla block reaction to a trigger-only
 * explosion asks {@code canTriggerBlocks}; knockback never does. Vanilla already answers false on the client.
 * 阻止激活冤魂（晋升风精灵商店的风弹）扔出的风弹切换门、活板门、栅栏门、按钮、拉杆、钟与蜡烛。
 * 原版方块对触发型爆炸的反应都会询问 canTriggerBlocks，击退不会；客户端原版本就返回 false。
 */
@Mixin(Explosion.class)
public abstract class WraithExplosionTriggerMixin {
    @Shadow
    public abstract @Nullable LivingEntity getCausingEntity();

    @Inject(method = "canTriggerBlocks()Z", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$activeWraithTriggersNoBlocks(CallbackInfoReturnable<Boolean> cir) {
        if (!WraithParticipationRules.mayTriggerBlocks(
                this.getCausingEntity() instanceof PlayerEntity player && WraithStateService.isActive(player))) {
            cir.setReturnValue(false);
        }
    }
}
