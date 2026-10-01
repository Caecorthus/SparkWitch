package dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.adapter;

import dev.doctor4t.wathe.util.ShopEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * Ordered whitelist of a disguise's shop; each id prefers the native entry built by the role's own
 * listener and otherwise uses the fallback (or is omitted when there is none). Evaluated on both sides,
 * so it must be deterministic and must not depend on a server player.
 * 伪装商店的有序白名单；每个 id 优先使用职业自身监听器构建的原生条目，否则使用回退条目（无回退则省略）。
 * 双端都会求值，因此必须确定且不依赖服务端玩家。
 */
public record DisguiseShopSpec(List<Entry> entries) {
    public static final DisguiseShopSpec EMPTY = new DisguiseShopSpec(List.of());
    /** Ids a disguise may never expose (the Demon Hunter sniff reads this shop). / 伪装永远不得暴露的 id。 */
    public static final Set<String> FORBIDDEN_IDS = Set.of("psycho_mode");

    public DisguiseShopSpec {
        entries = List.copyOf(entries);
        Set<String> seen = new HashSet<>();
        for (Entry entry : entries) {
            if (!seen.add(entry.id())) {
                throw new IllegalArgumentException("Duplicate disguise shop id " + entry.id());
            }
        }
    }

    public static DisguiseShopSpec of(Entry... entries) {
        return new DisguiseShopSpec(List.of(entries));
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public List<String> ids() {
        List<String> ids = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            ids.add(entry.id());
        }
        return List.copyOf(ids);
    }

    /** One whitelisted id with an optional fresh-entry fallback. / 一个白名单 id 及可选的新条目回退。 */
    public record Entry(String id, @Nullable Supplier<ShopEntry> fallback) {
        public Entry {
            Objects.requireNonNull(id, "id");
            if (id.isEmpty() || FORBIDDEN_IDS.contains(id)) {
                throw new IllegalArgumentException("Forbidden disguise shop id " + id);
            }
        }

        public static Entry nativeOnly(String id) {
            return new Entry(id, null);
        }

        public static Entry withFallback(String id, Supplier<ShopEntry> fallback) {
            return new Entry(id, Objects.requireNonNull(fallback, "fallback"));
        }
    }
}
