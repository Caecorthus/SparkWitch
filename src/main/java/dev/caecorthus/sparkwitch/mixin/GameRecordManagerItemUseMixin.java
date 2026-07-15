package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.roles.civilian.saint.SaintKarmaService;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Observes NoellesRoles' confirmed item-use record without cancelling or changing its logging path.
 * 只观察 NoellesRoles 已确认成功的物品使用记录，不取消也不改写其记录流程。
 */
@Mixin(GameRecordManager.class)
public abstract class GameRecordManagerItemUseMixin {
    @Inject(method = "recordItemUse", at = @At("HEAD"))
    private static void sparkwitch$triggerSaintKarma(
            ServerPlayerEntity actor,
            Identifier itemId,
            ServerPlayerEntity target,
            NbtCompound extra,
            CallbackInfo ci
    ) {
        SaintKarmaService.onRecordedItemUse(actor, itemId, target, extra);
    }
}
