package dev.caecorthus.sparkwitch.mixin;

import dev.caecorthus.sparkwitch.mixin.accessor.ServerItemCooldownManagerAccessor;
import dev.caecorthus.sparkwitch.roles.civilian.knight.KnightRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.WatheItems;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerItemCooldownManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * The Knight's derringer always uses a flat 1 s cooldown and ignores every trait modifier (for example Fast Hands);
 * the revolver keeps Wathe's police base cooldown and its normal trait interactions. Only the server-side cooldown
 * manager owns a player, so client bookkeeping is never touched.
 * 骑士的德林加固定 1 秒冷却，忽略所有词条修正（例如快手）；左轮仍保留 Wathe 的警察基础冷却与正常词条交互。
 * 只有服务端冷却管理器持有玩家，因此不会影响客户端冷却记录。
 */
@Mixin(value = ItemCooldownManager.class, priority = 800)
public abstract class KnightDerringerCooldownMixin {
    @ModifyArgs(method = "set", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/ItemCooldownManager$Entry;<init>(II)V"))
    private void sparkwitch$knightDerringerEntry(Args args, Item item, int duration) {
        if (sparkwitch$forcesKnightDerringer(item, duration)) {
            args.set(1, (int) args.get(0) + KnightRules.DERRINGER_COOLDOWN_TICKS);
        }
    }

    @ModifyArg(method = "set", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/player/ItemCooldownManager;onCooldownUpdate(Lnet/minecraft/item/Item;I)V"), index = 1)
    private int sparkwitch$knightDerringerPacket(Item item, int duration) {
        return sparkwitch$forcesKnightDerringer(item, duration)
                ? KnightRules.DERRINGER_COOLDOWN_TICKS : duration;
    }

    private boolean sparkwitch$forcesKnightDerringer(Item item, int duration) {
        if (duration <= 0 || item != WatheItems.DERRINGER
                || !((Object) this instanceof ServerItemCooldownManager manager)) {
            return false;
        }
        ServerPlayerEntity player = ((ServerItemCooldownManagerAccessor) manager).sparkwitch$getPlayer();
        return player != null && KnightRules.isKnight(
                GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }
}
