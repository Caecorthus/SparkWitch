package dev.caecorthus.sparkwitch.roles.special.wraith;

import dev.doctor4t.wathe.api.Faction;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

/**
 * Immutable state captured before Wathe mutates a confirmed-death candidate.
 * Wathe 修改待确认死亡玩家前保存的不可变快照。
 */
record WraithDeathSnapshot(
        Identifier originalRoleId,
        Faction originalFaction,
        WraithTaskSnapshot taskSnapshot,
        NbtCompound traitSnapshot,
        WraithState.Alignment alignment,
        boolean lastStandTriggeredBefore,
        int deathGameTime
) {
    WraithDeathSnapshot {
        traitSnapshot = traitSnapshot.copy();
    }

    @Override
    public NbtCompound traitSnapshot() {
        return traitSnapshot.copy();
    }
}
