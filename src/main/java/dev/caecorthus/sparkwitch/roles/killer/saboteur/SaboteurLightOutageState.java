package dev.caecorthus.sparkwitch.roles.killer.saboteur;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class SaboteurLightOutageState<K> {
    enum WatheEndDecision {
        NATIVE,
        KEEP_DARK,
        RESTORE_AFTER_NATIVE
    }

    record Restore<K>(K key, boolean lit, boolean active) {
    }

    private final Map<K, LampLease> leases = new HashMap<>();

    void beginLocal(K key, long expiryTick, boolean currentLit, boolean currentActive) {
        LampLease lease = leases.computeIfAbsent(key, ignored -> new LampLease(currentLit, currentActive));
        lease.localParticipated = true;
        lease.localExpiryTicks.add(expiryTick);
    }

    void beginWathe(K key, Object source, boolean currentLit, boolean currentActive) {
        LampLease lease = leases.computeIfAbsent(key, ignored -> new LampLease(currentLit, currentActive));
        lease.watheSources.add(source);
    }

    WatheEndDecision endWathe(K key, Object source) {
        LampLease lease = leases.get(key);
        if (lease == null || !lease.watheSources.remove(source)) {
            return WatheEndDecision.NATIVE;
        }
        if (!lease.localExpiryTicks.isEmpty() || !lease.watheSources.isEmpty()) {
            return WatheEndDecision.KEEP_DARK;
        }
        if (lease.localParticipated) {
            lease.restoreAfterWatheEnd = true;
            return WatheEndDecision.RESTORE_AFTER_NATIVE;
        }
        leases.remove(key);
        return WatheEndDecision.NATIVE;
    }

    Restore<K> finishWatheEnd(K key) {
        LampLease lease = leases.get(key);
        if (lease == null || !lease.restoreAfterWatheEnd) {
            return null;
        }
        leases.remove(key);
        return restore(key, lease);
    }

    List<Restore<K>> expireLocals(long currentTick) {
        List<Restore<K>> restores = new ArrayList<>();
        Iterator<Map.Entry<K, LampLease>> iterator = leases.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<K, LampLease> entry = iterator.next();
            LampLease lease = entry.getValue();
            lease.localExpiryTicks.removeIf(expiryTick -> expiryTick <= currentTick);
            if (lease.localParticipated
                    && lease.localExpiryTicks.isEmpty()
                    && lease.watheSources.isEmpty()
                    && !lease.restoreAfterWatheEnd) {
                restores.add(restore(entry.getKey(), lease));
                iterator.remove();
            }
        }
        return restores;
    }

    List<Restore<K>> clearLocals() {
        List<Restore<K>> restores = new ArrayList<>();
        Iterator<Map.Entry<K, LampLease>> iterator = leases.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<K, LampLease> entry = iterator.next();
            LampLease lease = entry.getValue();
            lease.localExpiryTicks.clear();
            if (lease.localParticipated
                    && lease.watheSources.isEmpty()
                    && !lease.restoreAfterWatheEnd) {
                restores.add(restore(entry.getKey(), lease));
                iterator.remove();
            }
        }
        return restores;
    }

    List<Restore<K>> clearAll() {
        List<Restore<K>> restores = new ArrayList<>();
        for (Map.Entry<K, LampLease> entry : leases.entrySet()) {
            if (entry.getValue().localParticipated) {
                restores.add(restore(entry.getKey(), entry.getValue()));
            }
        }
        leases.clear();
        return restores;
    }

    boolean hasLocal(K key) {
        LampLease lease = leases.get(key);
        return lease != null && !lease.localExpiryTicks.isEmpty();
    }

    boolean hasLocalWatheOverlap() {
        return leases.values().stream()
                .anyMatch(lease -> lease.localParticipated && !lease.watheSources.isEmpty());
    }

    boolean isEmpty() {
        return leases.isEmpty();
    }

    private static <K> Restore<K> restore(K key, LampLease lease) {
        return new Restore<>(key, lease.originalLit, lease.originalActive);
    }

    private static final class LampLease {
        private final boolean originalLit;
        private final boolean originalActive;
        private final List<Long> localExpiryTicks = new ArrayList<>();
        private final Set<Object> watheSources = Collections.newSetFromMap(new IdentityHashMap<>());
        private boolean localParticipated;
        private boolean restoreAfterWatheEnd;

        private LampLease(boolean originalLit, boolean originalActive) {
            this.originalLit = originalLit;
            this.originalActive = originalActive;
        }
    }
}
