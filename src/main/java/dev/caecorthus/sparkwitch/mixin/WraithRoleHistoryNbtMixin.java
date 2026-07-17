package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.compat.WraithLegacyRoleIds;
import dev.doctor4t.wathe.cca.RoleHistoryComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RoleHistoryComponent.class)
abstract class WraithRoleHistoryNbtMixin {
    /** Canonicalize exact saved role strings before Wathe builds history. / 在 Wathe 构建历史前规范化精确保存角色字符串。 */
    @Inject(method = "readFromNbt", at = @At("HEAD"))
    private void sparkwitch$migrateLegacyWraithRoleHistory(
            NbtCompound nbt,
            RegistryWrapper.WrapperLookup registryLookup,
            CallbackInfo ci
    ) {
        WraithLegacyRoleIds.migrateRoleHistoryNbt(nbt);
    }
}
