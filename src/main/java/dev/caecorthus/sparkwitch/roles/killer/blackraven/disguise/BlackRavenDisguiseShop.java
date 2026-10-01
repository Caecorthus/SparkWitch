package dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise;

import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.adapter.BlackRavenDisguiseAdapter;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.adapter.BlackRavenDisguiseAdapters;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.adapter.DisguiseShopSpec;
import dev.doctor4t.wathe.util.ShopEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Deterministic shop finalizer for a disguised Raven, identical on both sides so entry indexes match:
 * spec order, native entry preferred over the fallback, then native {@code sparktraits:} entries once.
 * 伪装黑羽鸦的确定性商店整理器，双端一致以保证条目索引相同：按规格顺序，原生条目优先于回退，
 * 然后追加一次原生 sparktraits: 条目。
 */
public final class BlackRavenDisguiseShop {
    /** SparkTraitsShopEntryPreserver convention: trait entries survive every role shop rebuild. / 词条条目约定。 */
    static final String SPARKTRAITS_ID_PREFIX = "sparktraits:";

    private BlackRavenDisguiseShop() {
    }

    /**
     * Keeps only whitelisted ids. A fallback is used only when no native entry exists and only if it builds an
     * entry with the same id, no stock limit and no initial cooldown (Wathe seeds both only at round start, from
     * the Raven's own shop); otherwise the id is omitted. Forbidden ids never leave this method.
     * 仅保留白名单 id。只有缺少原生条目时才使用回退，且回退必须构建同 id、无库存上限、无初始冷却的条目
     * （Wathe 只在开局按黑羽鸦自身商店初始化这两项）；否则省略该 id。禁用 id 永远不会输出。
     */
    public static List<ShopEntry> finalizeEntries(DisguiseShopSpec spec, List<ShopEntry> nativeEntries) {
        List<ShopEntry> natives = nativeEntries == null ? List.of() : nativeEntries;
        Set<ShopEntry> used = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<String> emitted = new HashSet<>();
        List<ShopEntry> out = new ArrayList<>();
        for (DisguiseShopSpec.Entry wanted : spec.entries()) {
            ShopEntry chosen = firstUnused(natives, wanted.id(), used);
            if (chosen != null) {
                used.add(chosen);
            } else {
                chosen = buildFallback(wanted);
            }
            if (chosen != null && emitted.add(chosen.id())) {
                out.add(chosen);
            }
        }
        for (ShopEntry entry : natives) {
            String id = entry == null ? null : entry.id();
            if (id != null && id.startsWith(SPARKTRAITS_ID_PREFIX) && !isForbidden(id) && emitted.add(id)) {
                out.add(entry);
            }
        }
        return out;
    }

    /** Spec of the player's acting role, or null when not disguised or unshipped. / 玩家扮演职业的规格；未伪装或未上线时为 null。 */
    public static @Nullable DisguiseShopSpec specFor(PlayerEntity player) {
        Identifier acting = BlackRavenActingRole.actingRoleId(player);
        if (acting == null) {
            return null;
        }
        BlackRavenDisguiseAdapter adapter = BlackRavenDisguiseAdapters.get(acting);
        return adapter == null ? null : adapter.shopSpec();
    }

    /** True when a fresh fallback entry is safe to expose for this spec id. / 回退条目可安全展示时为 true。 */
    static boolean isAcceptableFallback(String specId, @Nullable ShopEntry entry) {
        return entry != null
                && specId.equals(entry.id())
                && !isForbidden(entry.id())
                && !entry.hasStockLimit()
                && !entry.hasInitialCooldown();
    }

    private static @Nullable ShopEntry firstUnused(List<ShopEntry> natives, String id, Set<ShopEntry> used) {
        for (ShopEntry entry : natives) {
            if (entry != null && id.equals(entry.id()) && !used.contains(entry)) {
                return entry;
            }
        }
        return null;
    }

    private static @Nullable ShopEntry buildFallback(DisguiseShopSpec.Entry wanted) {
        Supplier<ShopEntry> fallback = wanted.fallback();
        if (fallback == null) {
            return null;
        }
        ShopEntry built = fallback.get();
        return isAcceptableFallback(wanted.id(), built) ? built : null;
    }

    private static boolean isForbidden(String id) {
        return DisguiseShopSpec.FORBIDDEN_IDS.contains(id);
    }
}
