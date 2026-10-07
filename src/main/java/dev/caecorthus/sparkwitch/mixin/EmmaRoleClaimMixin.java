package dev.caecorthus.sparkwitch.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.caecorthus.sparkwitch.roles.civilian.emma.EmmaRoundComponent;
import dev.caecorthus.sparkwitch.roles.civilian.emma.EmmaRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A saved assignment is restored, not acquired again; both live mutation overloads are guarded.
 * 存档恢复不是再次取得职业；两个实时写入入口均受唯一声明约束。 */
@Mixin(value = GameWorldComponent.class, remap = false)
public abstract class EmmaRoleClaimMixin {
    @Shadow @Final private World world;
    @Unique private boolean sparkwitch$restoringRoles;

    @WrapMethod(method = "readFromNbt")
    private void sparkwitch$restoreRoles(NbtCompound tag, RegistryWrapper.WrapperLookup lookup, Operation<Void> original) {
        boolean previous = sparkwitch$restoringRoles;
        sparkwitch$restoringRoles = true;
        try { original.call(tag, lookup); } finally { sparkwitch$restoringRoles = previous; }
    }

    @Inject(method = "addRole(Lnet/minecraft/entity/player/PlayerEntity;Ldev/doctor4t/wathe/api/Role;)V", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$claimPlayer(PlayerEntity player, Role role, CallbackInfo ci) {
        if (!sparkwitch$mayAssign(player.getUuid(), role)) ci.cancel();
    }

    @Inject(method = "addRole(Ljava/util/UUID;Ldev/doctor4t/wathe/api/Role;)V", at = @At("HEAD"), cancellable = true)
    private void sparkwitch$claimUuid(UUID player, Role role, CallbackInfo ci) {
        if (!sparkwitch$mayAssign(player, role)) ci.cancel();
    }

    @Unique private boolean sparkwitch$mayAssign(UUID player, Role role) {
        if (sparkwitch$restoringRoles || !(world instanceof ServerWorld) || !EmmaRules.isEmma(role)) return true;
        GameWorldComponent game = (GameWorldComponent) (Object) this;
        return EmmaRoundComponent.KEY.get(world).claim(player, EmmaRules.isEmma(game.getRole(player)));
    }
}
