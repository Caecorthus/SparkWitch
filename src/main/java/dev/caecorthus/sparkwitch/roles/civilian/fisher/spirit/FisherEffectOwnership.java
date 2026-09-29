package dev.caecorthus.sparkwitch.roles.civilian.fisher.spirit;

/** A foreign merge permanently revokes this instance's removal lease. / 外部合并永久撤销该实例的移除权。 */
final class FisherEffectOwnership<T> {
    private final T acquired;
    private boolean exclusive = true;

    FisherEffectOwnership(T acquired) {
        this.acquired = acquired;
    }

    void relinquish() {
        exclusive = false;
    }

    boolean owns(T current) {
        return acquired != null && exclusive && acquired == current;
    }
}
