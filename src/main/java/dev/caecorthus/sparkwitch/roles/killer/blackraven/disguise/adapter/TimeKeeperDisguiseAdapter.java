package dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.adapter;

import dev.doctor4t.wathe.cca.GameTimeComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.ModItems;

/**
 * Disguise parity surface of the Time Keeper: TimekeeperShopHandler reads isRole, so the native entry (and any
 * SparkTraits redirect of it) is kept; the fallback replicates it only if the native entry is missing.
 * 时间管理员的伪装对等面：TimekeeperShopHandler 读取 isRole，因此保留原生条目（及 SparkTraits 对其的改写）；
 * 仅在原生条目缺失时使用复刻的回退条目。
 */
public final class TimeKeeperDisguiseAdapter implements BlackRavenDisguiseAdapter {
    public static final Identifier ROLE_ID = Identifier.of("noellesroles", "time_keeper");
    /**
     * Legacy ShopEntry(ItemStack, int, Type) id: Item#toString with ':' replaced by '_'.
     * 旧版 ShopEntry(ItemStack, int, Type) 的 id：Item#toString 并把 ':' 替换为 '_'。
     */
    public static final String REDUCE_TIME_ENTRY_ID = "noellesroles_timekeeper_reduce_time";
    static final int REDUCE_TIME_PRICE = 100;
    static final int REDUCE_TIME_TICKS = 900;
    private static final DisguiseShopSpec SHOP = DisguiseShopSpec.of(
            DisguiseShopSpec.Entry.withFallback(REDUCE_TIME_ENTRY_ID, TimeKeeperDisguiseAdapter::reduceTimeFallback)
    );

    @Override
    public Identifier roleId() {
        return ROLE_ID;
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

    /** Replica of TimekeeperShopHandler's entry (no stock, no initial cooldown). / 复刻 TimekeeperShopHandler 条目。 */
    static ShopEntry reduceTimeFallback() {
        ItemStack displayStack = new ItemStack(ModItems.TIMEKEEPER_REDUCE_TIME);
        displayStack.set(DataComponentTypes.CUSTOM_NAME, Text.translatable("item.noellesroles.timekeeper_reduce_time"));
        return new ShopEntry.Builder(REDUCE_TIME_ENTRY_ID, displayStack, REDUCE_TIME_PRICE, ShopEntry.Type.TOOL)
                .onBuy(buyer -> {
                    GameTimeComponent.KEY.get(buyer.getWorld()).addTime(-REDUCE_TIME_TICKS);
                    return true;
                })
                .build();
    }
}
