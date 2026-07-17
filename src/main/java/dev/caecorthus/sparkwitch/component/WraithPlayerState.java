package dev.caecorthus.sparkwitch.component;

import dev.caecorthus.sparkwitch.roles.special.wraith.WraithState;

/** Pure state machine used by the Wraith player component. */
final class WraithPlayerState {
    private boolean active;
    private boolean restricted;
    private int completedTasks;
    private WraithState.Alignment alignment;
    private boolean promotionPending;

    boolean isActive() {
        return active;
    }

    boolean isRestricted() {
        return active && restricted;
    }

    boolean isPromoted() {
        return active && !restricted;
    }

    int getCompletedTasks() {
        return completedTasks;
    }

    WraithState.Alignment getAlignment() {
        return alignment;
    }

    boolean isPromotionPending() {
        return promotionPending;
    }

    void activate(WraithState.Alignment alignment) {
        restore(true, true, 0, alignment, false);
    }

    int recordTaskCompletion() {
        if (active) {
            completedTasks++;
        }
        return completedTasks;
    }

    boolean setPromotionPending(boolean pending) {
        if (promotionPending == pending) {
            return false;
        }
        promotionPending = pending;
        return true;
    }

    boolean promote() {
        if (!active) {
            return false;
        }
        restricted = false;
        promotionPending = false;
        return true;
    }

    boolean clear() {
        if (!active && !restricted && completedTasks == 0 && alignment == null && !promotionPending) {
            return false;
        }
        restore(false, false, 0, null, false);
        return true;
    }

    void restore(
            boolean active,
            boolean restricted,
            int completedTasks,
            WraithState.Alignment alignment,
            boolean promotionPending
    ) {
        this.active = active;
        this.restricted = active && restricted;
        this.completedTasks = active ? Math.max(0, completedTasks) : 0;
        this.alignment = active ? alignment : null;
        this.promotionPending = active && promotionPending;
    }
}
