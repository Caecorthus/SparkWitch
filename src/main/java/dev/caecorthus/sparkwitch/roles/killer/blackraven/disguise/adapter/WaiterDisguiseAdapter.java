package dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.adapter;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.waiter.WaiterPlayerComponent;

/**
 * Disguise parity surface of the Waiter: the shop, feeding and platter mixins and the sync gate read isRole. The shop
 * entry is native only; if NoellesRoles stops emitting it, the entry is omitted (fail closed).
 * 服务员的伪装对等面：商店、喂食与托盘 mixin 以及同步门控都读取 isRole。商店条目仅用原生；诺艾尔职业不再生成时
 * 直接省略（失败关闭）。
 */
public final class WaiterDisguiseAdapter implements BlackRavenDisguiseAdapter {
    public static final Identifier ROLE_ID = Identifier.of("noellesroles", "waiter");
    public static final String SERVICE_ENTRY_ID = "waiter_random_food_or_drink";
    private static final DisguiseShopSpec SHOP = DisguiseShopSpec.of(DisguiseShopSpec.Entry.nativeOnly(SERVICE_ENTRY_ID));

    @Override
    public Identifier roleId() {
        return ROLE_ID;
    }

    /** Mirrors NoellesRoles' RoleAssigned WAITER reset. / 镜像诺艾尔职业 RoleAssigned 中服务员的重置。 */
    @Override
    public void onFirstEntry(ServerPlayerEntity player, FirstEntry entry) {
        WaiterPlayerComponent.KEY.get(player).reset();
    }

    @Override
    public DisguiseShopSpec shopSpec() {
        return SHOP;
    }

    @Override
    public boolean moneyVisible() {
        return true;
    }

    @Override
    public DisguiseTaskReward taskReward() {
        return DisguiseTaskReward.NOELLES_NATIVE;
    }
}
