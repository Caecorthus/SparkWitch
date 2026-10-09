package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.civilian.windspirit.WindSpiritFallRecords;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Notes a promoted Wind Spirit's wind charge catching a player, for the {@code sparkwitch:wind_spirit_fall} record
 * only. {@code Explosion} calls {@code onExplodedBy} on the server for every entity in the blast, after knockback;
 * this observes at TAIL and changes nothing (knockback, fall damage and kill credit stay vanilla/Wathe).
 * 仅为 {@code sparkwitch:wind_spirit_fall} 记录晋升风精灵风弹波及玩家的情况。{@code Explosion} 在服务端对爆炸范围内每个实体
 * 施加击退后调用 {@code onExplodedBy}；本注入在 TAIL 只做观察，不改变任何东西（击退、摔落伤害与击杀归属保持原版/Wathe 行为）。
 */
@Mixin(ServerPlayerEntity.class)
public abstract class WindSpiritKnockRecordMixin {
    @Inject(method = "onExplodedBy", at = @At("TAIL"))
    private void sparkwitch$noteWindSpiritKnock(Entity entity, CallbackInfo ci) {
        WindSpiritFallRecords.onExplodedBy((ServerPlayerEntity) (Object) this, entity);
    }
}
