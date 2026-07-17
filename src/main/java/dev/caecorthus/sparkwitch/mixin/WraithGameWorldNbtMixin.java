package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.compat.WraithLegacyRoleIds;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameWorldComponent.class)
abstract class WraithGameWorldNbtMixin {
    /** Canonicalize only exact legacy ids before Wathe resolves role lists. / 仅在 Wathe 解析角色列表前规范化精确旧 id。 */
    @Inject(method = "readFromNbt", at = @At("HEAD"))
    private void sparkwitch$migrateLegacyWraithRoles(
            NbtCompound nbt,
            RegistryWrapper.WrapperLookup registryLookup,
            CallbackInfo ci
    ) {
        WraithLegacyRoleIds.migrateGameWorldNbt(nbt);
    }
}
