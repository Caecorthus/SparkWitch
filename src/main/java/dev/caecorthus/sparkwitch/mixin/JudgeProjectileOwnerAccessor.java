package dev.caecorthus.sparkwitch.mixin;

import net.minecraft.entity.projectile.ProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

/** Vanilla persists owner UUID even if getOwner cannot resolve an entity. / 原版 owner UUID 在离线实体无法解析时仍存在。 */
@Mixin(ProjectileEntity.class)
public interface JudgeProjectileOwnerAccessor {
    @Accessor("ownerUuid") UUID sparkwitch$judgeOwnerUuid();
}
