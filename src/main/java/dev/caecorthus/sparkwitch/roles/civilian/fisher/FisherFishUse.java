package dev.caecorthus.sparkwitch.roles.civilian.fisher;

import dev.caecorthus.sparkwitch.roles.civilian.fisher.item.FisherFishKind;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/** Server-side effect of using one fish; returns whether one was consumed. / 服务端吃鱼效果；返回是否消耗了一条。WP2 stub. */
public final class FisherFishUse {
    private FisherFishUse() {
    }

    public static boolean use(ServerPlayerEntity player, ItemStack stack, FisherFishKind kind) {
        return false;
    }
}
