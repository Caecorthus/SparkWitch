package dev.caecorthus.sparkwitch.mixin.hunter;

import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterTrapEntity;
import dev.doctor4t.wathe.cca.PlayerPoisonComponent;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures only accepted poison applications with their real source identifier; canceled attempts never replace attribution.
 * 只记录真正生效的毒源标识；被取消的下毒不会覆盖捕兽夹归属。
 */
@Mixin(PlayerPoisonComponent.class)
public abstract class PlayerPoisonComponentHunterSourceMixin {
    @Shadow
    @Final
    private PlayerEntity player;

    @Inject(
            method = "setPoisonTicks(ILjava/util/UUID;Lnet/minecraft/util/Identifier;Lnet/minecraft/nbt/NbtCompound;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/api/event/PlayerPoisoned$After;afterPlayerPoisoned(Lnet/minecraft/entity/player/PlayerEntity;ILjava/util/UUID;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void sparkwitch$captureAcceptedPoisonSource(
            int ticks,
            UUID poisonerUuid,
            Identifier source,
            NbtCompound extra,
            CallbackInfo callbackInfo
    ) {
        HunterPlayerComponent component = HunterPlayerComponent.KEY.get(player);
        component.onAcceptedPoisonApplication(source);
        if (HunterTrapEntity.POISON_SOURCE.equals(source)) {
            UUID placerUuid = extra != null && extra.containsUuid(HunterTrapEntity.POISON_PLACER_NBT_KEY)
                    ? extra.getUuid(HunterTrapEntity.POISON_PLACER_NBT_KEY)
                    : null;
            component.setTrapPoisonAttribution(
                    placerUuid,
                    poisonerUuid,
                    player.getWorld().getTime(),
                    ticks
            );
        }
    }
}
