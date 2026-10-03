package dev.caecorthus.sparkwitch.roles.civilian.prophet;

import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Pure, world-identity-keyed storage behind {@link ProphetDeathLedger}; a record is visible only under the match key
 * it was written in, and a write under a new key drops the previous match.
 * {@link ProphetDeathLedger} 背后的纯存储，按世界对象隔离；记录只在写入时的对局键下可见，换对局键写入即丢弃旧对局。
 */
final class ProphetDeathBook<W> {
    private static final Comparator<ProphetDeathRecord> BY_NAME = Comparator
            .comparing(ProphetDeathRecord::victimName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(ProphetDeathRecord::victimName)
            .thenComparing(ProphetDeathRecord::victim);

    private final Map<W, Page> pages = new IdentityHashMap<>();

    /** Responsibility first, then the direct killer; a self-kill means no killer. / 先取归因责任人，再取直接凶手；自杀视为无人行凶。 */
    static @Nullable UUID resolveResponsible(UUID victim, @Nullable UUID attributed, @Nullable UUID killer) {
        UUID responsible = attributed != null ? attributed : killer;
        return victim.equals(responsible) ? null : responsible;
    }

    /** Same victim again overwrites: the last death wins. / 同一受害者再次写入覆盖旧记录：以最后一次死亡为准。 */
    void record(W world, String match, ProphetDeathRecord record) {
        Objects.requireNonNull(match);
        Page page = pages.get(world);
        if (page == null || !page.match.equals(match)) {
            page = new Page(match);
            pages.put(world, page);
        }
        page.records.put(record.victim(), record);
    }

    Optional<ProphetDeathRecord> get(W world, @Nullable String match, @Nullable UUID victim) {
        Page page = page(world, match);
        return page == null || victim == null ? Optional.empty() : Optional.ofNullable(page.records.get(victim));
    }

    List<ProphetDeathRecord> deadCandidates(W world, @Nullable String match, Predicate<UUID> deadParticipant) {
        Page page = page(world, match);
        if (page == null) {
            return List.of();
        }
        return page.records.values().stream()
                .filter(record -> deadParticipant.test(record.victim()))
                .sorted(BY_NAME)
                .toList();
    }

    void clearWorld(W world) {
        pages.remove(world);
    }

    void clearAll() {
        pages.clear();
    }

    private @Nullable Page page(W world, @Nullable String match) {
        Page page = pages.get(world);
        return page == null || match == null || !page.match.equals(match) ? null : page;
    }

    private static final class Page {
        private final String match;
        private final Map<UUID, ProphetDeathRecord> records = new HashMap<>();

        private Page(String match) {
            this.match = match;
        }
    }
}
