package dev.caecorthus.sparkwitch.client.mixin.witchmaiden;

import dev.caecorthus.sparkwitch.client.witchmaiden.PoisonAppleParticleClientHooks;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.PoisonApplePlateAccess;
import dev.doctor4t.wathe.block_entity.BeveragePlateBlockEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Client-only rising red dust; the server syncs only whether the Poison Apple trap is armed.
 * 仅在客户端生成向上飘散的红尘；服务端只同步毒苹果陷阱是否已布置。
 */
@Environment(EnvType.CLIENT)
@Mixin(BeveragePlateBlockEntity.class)
public abstract class BeveragePlateBlockEntityPoisonAppleParticleMixin {
    private static final DustParticleEffect PARTICLE = new DustParticleEffect(new Vector3f(1.0f, 0.0f, 0.0f), 1.0f);

    @Inject(method = "clientTick", at = @At("TAIL"))
    private static void sparkwitch$spawnPoisonAppleDust(
            World world,
            BlockPos pos,
            BlockState state,
            BlockEntity blockEntity,
            CallbackInfo ci
    ) {
        if (!(blockEntity instanceof PoisonApplePlateAccess poisonApple)
                || !poisonApple.sparkwitch$isPoisonAppleArmed()
                || !PoisonAppleParticleClientHooks.canSee(MinecraftClient.getInstance().player)
                || world.getRandom().nextBetween(0, 20) < 17) {
            return;
        }
        double offsetX = (world.getRandom().nextDouble() - 0.5D) * 0.3D;
        double offsetZ = (world.getRandom().nextDouble() - 0.5D) * 0.3D;
        world.addParticle(
                PARTICLE,
                pos.getX() + 0.5D + offsetX,
                pos.getY() + 0.2D,
                pos.getZ() + 0.5D + offsetZ,
                0.0D,
                0.15D,
                0.0D
        );
    }
}
