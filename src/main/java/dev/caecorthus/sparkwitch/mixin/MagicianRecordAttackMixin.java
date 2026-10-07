package dev.caecorthus.sparkwitch.mixin;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianServerHooks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PlayerEntity.class)
public abstract class MagicianRecordAttackMixin extends LivingEntity {
 protected MagicianRecordAttackMixin(EntityType<? extends LivingEntity> type, World world){super(type,world);}
 @Inject(method="attack",at=@At("HEAD")) private void sparkwitch$record(Entity target,CallbackInfo ci){if((Object)this instanceof ServerPlayerEntity player) MagicianServerHooks.recordAttack(player);}
}
