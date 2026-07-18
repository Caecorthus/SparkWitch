package dev.caecorthus.sparkwitch.roles.special.wraith.conversion;

import dev.caecorthus.sparkwitch.roles.special.wraith.WraithState;
import dev.caecorthus.sparkwitch.roles.special.wraith.progression.WraithTaskSnapshot;
import dev.doctor4t.wathe.api.Faction;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

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
        @Nullable UUID creditedKillerUuid,
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
