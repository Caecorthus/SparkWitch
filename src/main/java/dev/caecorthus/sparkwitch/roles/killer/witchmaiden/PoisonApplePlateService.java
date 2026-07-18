package dev.caecorthus.sparkwitch.roles.killer.witchmaiden;

import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.NoellesToxicologistBridge;
import dev.doctor4t.wathe.block_entity.BeveragePlateBlockEntity;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.WatheDataComponentTypes;
import dev.doctor4t.wathe.record.GameRecordManager;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** Server-authoritative Poison Apple platter transitions and NoellesRoles antidote compatibility. */
public final class PoisonApplePlateService {
    private PoisonApplePlateService() {
    }

    public static void refreshMatch(PoisonApplePlateAccess plate) {
        plate.sparkwitch$clearIfMatchChanged(activeMatchId());
    }

    public static boolean isHoldingPoisonApple(PlayerEntity player) {
        return player.getMainHandStack().isOf(SparkWitchItems.poisonApple());
    }

    public static boolean tryArm(PlayerEntity player, PoisonApplePlateAccess plate) {
        ItemStack heldStack = player.getStackInHand(Hand.MAIN_HAND);
        UUID matchUuid = activeMatchId();
        plate.sparkwitch$clearIfMatchChanged(matchUuid);
        if (matchUuid == null
                || !isHoldingPoisonApple(player)
                || !plate.sparkwitch$armPoisonApple(player.getUuid(), matchUuid)) {
            return false;
        }
        heldStack.decrement(1);
        player.getInventory().markDirty();
        return true;
    }

    public static void recordSuccessfulTake(PlayerEntity player, PoisonApplePlateAccess plate) {
        UUID matchUuid = activeMatchId();
        plate.sparkwitch$clearIfMatchChanged(matchUuid);
        if (matchUuid == null) {
            return;
        }
        UUID poisonerUuid = plate.sparkwitch$recordSuccessfulTake(matchUuid);
        if (poisonerUuid == null) {
            return;
        }
        ItemStack takenStack = player.getMainHandStack();
        takenStack.set(WatheDataComponentTypes.POISONER, poisonerUuid.toString());
        PoisonAppleDrinkMarker.mark(takenStack);
    }

    public static boolean isReadyAntidote(PlayerEntity player) {
        var role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return PoisonAppleAntidoteRules.canCure(
                NoellesToxicologistBridge.isExactToxicologist(role),
                NoellesToxicologistBridge.holdsAntidote(player),
                NoellesToxicologistBridge.isAntidoteCoolingDown(player)
        );
    }

    public static boolean cureWithAntidote(
            World world,
            BlockPos pos,
            PlayerEntity player,
            BeveragePlateBlockEntity plate,
            PoisonApplePlateAccess poisonApple,
            boolean antidoteWasReady,
            boolean hadNativePoison
    ) {
        if (!antidoteWasReady || !poisonApple.sparkwitch$isPoisonAppleArmed()) {
            return false;
        }
        if (hadNativePoison && plate.getPoisoner() != null) {
            return false;
        }

        poisonApple.sparkwitch$clearPoisonApple();
        if (hadNativePoison) {
            // NoellesRoles already owns the shared sound, cooldown, and replay when it cured native poison.
            // 同时存在原生毒时，声音、冷却和回放仍只由 NoellesRoles 产生一次。
            return true;
        }

        world.playSound(
                null,
                pos,
                SoundEvents.ENTITY_PLAYER_BURP,
                SoundCategory.PLAYERS,
                1.0f,
                1.2f
        );
        NoellesToxicologistBridge.startPlateAntidoteCooldown(player);
        if (player instanceof ServerPlayerEntity serverPlayer) {
            NbtCompound extra = new NbtCompound();
            extra.putString("action", "cure_plate");
            GameRecordManager.putBlockPos(extra, "pos", pos);
            GameRecordManager.recordItemUse(
                    serverPlayer,
                    NoellesToxicologistBridge.antidoteItemId(),
                    null,
                    extra
            );
        }
        return true;
    }

    public static void clearLoadedPlates() {
        PoisonApplePlateTracker.clearLoadedPlates();
    }

    /** Version-stable match lookup shared with the plate NBT mixin. / 与餐盘 NBT mixin 共用的当前比赛查询。 */
    public static @Nullable UUID activeMatchId() {
        GameRecordManager.MatchRecord match = GameRecordManager.getCurrentMatch();
        return GameRecordManager.hasActiveMatch() && match != null ? match.getMatchId() : null;
    }
}
