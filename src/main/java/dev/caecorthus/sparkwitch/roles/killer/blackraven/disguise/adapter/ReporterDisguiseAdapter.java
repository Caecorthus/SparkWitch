package dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.adapter;

import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.reporter.ReporterPlayerComponent;

/**
 * Disguise parity surface of the Reporter: the mark packet, key dispatch, HUD, instinct and additive shop read isRole.
 * The "note" id is unambiguous only because the killer shop prefill (which also emits "note") is suppressed for a
 * disguised Raven. The mark persists across switches; only the first entry resets it.
 * 记者的伪装对等面：标记数据包、按键分发、HUD、本能与追加商店均读取 isRole。"note" id 之所以不产生歧义，
 * 是因为伪装中的黑羽鸦不会预填杀手商店（其中同样有 "note"）。标记跨切换保留，仅首次进入时重置。
 */
public final class ReporterDisguiseAdapter implements BlackRavenDisguiseAdapter {
    public static final Identifier ROLE_ID = Identifier.of("noellesroles", "reporter");
    public static final String NOTE_ENTRY_ID = "note";
    /** NoellesRoles RoleAssigned REPORTER: 30 s opening ability cooldown. / 诺艾尔职业记者开局 30 秒能力冷却。 */
    static final int INITIAL_ABILITY_COOLDOWN_TICKS = 20 * 30;
    static final int NOTE_PRICE = 50;
    static final int NOTE_COUNT = 4;
    private static final DisguiseShopSpec SHOP = DisguiseShopSpec.of(
            DisguiseShopSpec.Entry.withFallback(NOTE_ENTRY_ID, ReporterDisguiseAdapter::noteFallback)
    );

    @Override
    public Identifier roleId() {
        return ROLE_ID;
    }

    @Override
    public int initialAbilityCooldownTicks() {
        return INITIAL_ABILITY_COOLDOWN_TICKS;
    }

    /** Mirrors NoellesRoles' RoleAssigned REPORTER reset. / 镜像诺艾尔职业 RoleAssigned 中记者的重置。 */
    @Override
    public void onFirstEntry(ServerPlayerEntity player, FirstEntry entry) {
        ReporterPlayerComponent.KEY.get(player).reset();
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

    /** Replica of ReporterShopHandler's entry (no stock, no initial cooldown). / 复刻 ReporterShopHandler 条目。 */
    static ShopEntry noteFallback() {
        return new ShopEntry.Builder(NOTE_ENTRY_ID, new ItemStack(WatheItems.NOTE, NOTE_COUNT), NOTE_PRICE, ShopEntry.Type.TOOL)
                .build();
    }
}
