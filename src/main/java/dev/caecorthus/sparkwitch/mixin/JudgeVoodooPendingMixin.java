package dev.caecorthus.sparkwitch.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeKillAttribution;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeVoodooCause;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.voodoo.VoodooPlayerComponent;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** The existing pending countdown owns the saved UUID and reset/NBT lifecycle. / 保存责任 UUID 随现有倒计时重置与持久化。 */
@Mixin(VoodooPlayerComponent.class)
public abstract class JudgeVoodooPendingMixin implements JudgeVoodooCause, AutoSyncedComponent {
    @Unique private UUID sparkwitch$pendingVoodooActor;

    @Override
    public void sparkwitch$setPendingVoodooActor(UUID actor) {
        sparkwitch$pendingVoodooActor = actor;
    }

    @Inject(method = {"reset", "startPendingDeath"}, at = @At("HEAD"))
    private void sparkwitch$clearVoodooCause(CallbackInfo ci) {
        sparkwitch$pendingVoodooActor = null;
    }

    @WrapOperation(method = "serverTick", at = @At(value = "INVOKE",
            target = "Ldev/doctor4t/wathe/game/GameFunctions;killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;)V"))
    private void sparkwitch$scopeVoodoo(ServerPlayerEntity victim, boolean body, ServerPlayerEntity killer,
                                       Identifier reason, Operation<Void> original) {
        UUID actor = sparkwitch$pendingVoodooActor;
        sparkwitch$pendingVoodooActor = null;
        JudgeKillAttribution.runWith(victim.getServerWorld(), actor,
                () -> original.call(victim, body, killer, reason));
    }

    /** The default CCA transport serializes NBT; never reveal the hidden curse owner to its victim.
     * CCA 默认传输会序列化 NBT，不得把隐藏的诅咒施加者同步给受害者。 */
    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        NbtCompound publicState = new NbtCompound();
        ((VoodooPlayerComponent) (Object) this).writeToNbt(publicState, buf.getRegistryManager());
        publicState.remove("sparkwitch:judge_voodoo_actor");
        buf.writeNbt(publicState);
    }

    @Inject(method = "writeToNbt", at = @At("TAIL"))
    private void sparkwitch$writeVoodooCause(NbtCompound tag, RegistryWrapper.WrapperLookup registries, CallbackInfo ci) {
        if (sparkwitch$pendingVoodooActor != null) tag.putUuid("sparkwitch:judge_voodoo_actor", sparkwitch$pendingVoodooActor);
        else tag.remove("sparkwitch:judge_voodoo_actor");
    }

    @Inject(method = "readFromNbt", at = @At("TAIL"))
    private void sparkwitch$readVoodooCause(NbtCompound tag, RegistryWrapper.WrapperLookup registries, CallbackInfo ci) {
        sparkwitch$pendingVoodooActor = tag.containsUuid("sparkwitch:judge_voodoo_actor")
                ? tag.getUuid("sparkwitch:judge_voodoo_actor") : null;
    }
}
