package dev.caecorthus.sparkwitch.mixin.witchmaiden;

import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.PoisonApplePlateAccess;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.PoisonApplePlateNbt;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.PoisonApplePlateState;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.PoisonApplePlateService;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.PoisonApplePlateTracker;
import dev.doctor4t.wathe.block_entity.BeveragePlateBlockEntity;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds private server authority and an armed-only client view without touching Wathe's native poisoner.
 * 增加服务端私密权威状态与客户端 armed 位，不写入 Wathe 的原生 poisoner。
 */
@Mixin(BeveragePlateBlockEntity.class)
public abstract class BeveragePlateBlockEntityPoisonAppleMixin implements PoisonApplePlateAccess {
    @Unique
    private @Nullable PoisonApplePlateState sparkwitch$poisonAppleState;
    @Unique
    private boolean sparkwitch$clientPoisonAppleArmed;

    @Override
    public boolean sparkwitch$isPoisonAppleArmed() {
        return sparkwitch$poisonAppleState != null || sparkwitch$clientPoisonAppleArmed;
    }

    @Override
    public boolean sparkwitch$armPoisonApple(UUID placerUuid, UUID matchUuid) {
        if (sparkwitch$isPoisonAppleArmed()) {
            return false;
        }
        sparkwitch$poisonAppleState = PoisonApplePlateState.armed(placerUuid, matchUuid);
        sparkwitch$clientPoisonAppleArmed = true;
        PoisonApplePlateTracker.track(this);
        sparkwitch$syncVisibleState();
        return true;
    }

    @Override
    public @Nullable UUID sparkwitch$recordSuccessfulTake(UUID matchUuid) {
        if (sparkwitch$poisonAppleState == null) {
            return null;
        }
        PoisonApplePlateState.TakeResult result = sparkwitch$poisonAppleState.onSuccessfulTake(matchUuid);
        sparkwitch$poisonAppleState = result.nextState();
        sparkwitch$clientPoisonAppleArmed = sparkwitch$poisonAppleState != null;
        if (sparkwitch$poisonAppleState == null) {
            PoisonApplePlateTracker.untrack(this);
        }
        sparkwitch$syncVisibleState();
        return result.poisonerUuid();
    }

    @Override
    public void sparkwitch$clearPoisonApple() {
        if (!sparkwitch$isPoisonAppleArmed()) {
            PoisonApplePlateTracker.untrack(this);
            return;
        }
        sparkwitch$poisonAppleState = null;
        sparkwitch$clientPoisonAppleArmed = false;
        PoisonApplePlateTracker.untrack(this);
        sparkwitch$syncVisibleState();
    }

    @Override
    public void sparkwitch$clearIfMatchChanged(@Nullable UUID matchUuid) {
        if (sparkwitch$poisonAppleState != null
                && !Objects.equals(sparkwitch$poisonAppleState.matchUuid(), matchUuid)) {
            sparkwitch$clearPoisonApple();
        }
    }

    @Inject(method = "writeNbt", at = @At("TAIL"))
    private void sparkwitch$writePoisonApple(
            NbtCompound nbt,
            RegistryWrapper.WrapperLookup registryLookup,
            CallbackInfo ci
    ) {
        PoisonApplePlateNbt.writePersistent(nbt, sparkwitch$poisonAppleState);
    }

    @Inject(method = "readNbt", at = @At("TAIL"))
    private void sparkwitch$readPoisonApple(
            NbtCompound nbt,
            RegistryWrapper.WrapperLookup registryLookup,
            CallbackInfo ci
    ) {
        PoisonApplePlateState decodedState = PoisonApplePlateNbt.readPersistent(nbt);
        boolean rejectedStaleState = decodedState != null
                && !decodedState.belongsTo(PoisonApplePlateService.activeMatchId());
        sparkwitch$poisonAppleState = rejectedStaleState ? null : decodedState;
        sparkwitch$clientPoisonAppleArmed = sparkwitch$poisonAppleState != null
                || nbt.getBoolean(PoisonApplePlateNbt.ARMED_KEY);
        if (rejectedStaleState) {
            ((BlockEntity) (Object) this).markDirty();
        }
        if (sparkwitch$poisonAppleState != null) {
            PoisonApplePlateTracker.track(this);
        } else {
            PoisonApplePlateTracker.untrack(this);
        }
    }

    @Inject(method = "toInitialChunkDataNbt", at = @At("RETURN"))
    private void sparkwitch$stripSensitivePoisonAppleData(
            RegistryWrapper.WrapperLookup registryLookup,
            CallbackInfoReturnable<NbtCompound> cir
    ) {
        PoisonApplePlateNbt.stripForClient(cir.getReturnValue(), sparkwitch$isPoisonAppleArmed());
    }

    @Unique
    private void sparkwitch$syncVisibleState() {
        BlockEntity plate = (BlockEntity) (Object) this;
        plate.markDirty();
        World world = plate.getWorld();
        if (world != null && !world.isClient) {
            world.updateListeners(plate.getPos(), plate.getCachedState(), plate.getCachedState(), 3);
        }
    }
}
