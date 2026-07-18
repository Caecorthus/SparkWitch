package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.civilian.saint.SaintKarmaService;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaReplayService;
import dev.caecorthus.sparkwitch.roles.special.wraith.conversion.WraithSwapperFallAttribution;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps Vendetta terminal replay exclusive, then observes remaining confirmed item-use records.
 * 保证仇杀终止回放唯一，再观察其余已确认成功的物品使用记录。
 */
@Mixin(GameRecordManager.class)
public abstract class GameRecordManagerItemUseMixin {
    @Inject(method = "recordItemUse", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$triggerSaintKarma(
            ServerPlayerEntity actor,
            Identifier itemId,
            ServerPlayerEntity target,
            NbtCompound extra,
            CallbackInfo ci
    ) {
        if (VendettaReplayService.shouldSuppressItemRecord(actor, target)) {
            ci.cancel();
            return;
        }
        SaintKarmaService.onRecordedItemUse(actor, itemId, target, extra);
    }

    @Inject(method = "recordSkillUse", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$hideVendettaTerminalSkillRecord(
            ServerPlayerEntity actor,
            Identifier skillId,
            ServerPlayerEntity target,
            NbtCompound extra,
            CallbackInfo ci
    ) {
        if (VendettaReplayService.shouldSuppressSkillRecord(actor, target)) {
            ci.cancel();
        }
    }

    /** Observes only NoellesRoles' already-confirmed successful Swapper replay. */
    @Inject(method = "recordSkillUse", at = @At("TAIL"))
    private static void sparkwitch$captureSuccessfulSwapper(
            ServerPlayerEntity actor,
            Identifier skillId,
            ServerPlayerEntity target,
            NbtCompound extra,
            CallbackInfo ci
    ) {
        WraithSwapperFallAttribution.onRecordedSkillUse(actor, skillId, target, extra);
    }
}
