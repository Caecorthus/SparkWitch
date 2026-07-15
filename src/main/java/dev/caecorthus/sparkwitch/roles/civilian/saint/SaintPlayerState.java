package dev.caecorthus.sparkwitch.roles.civilian.saint;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.RegistryByteBuf;

/**
 * Owns Saint's player-local ability state and the client-visible mirror of UUID-owned Karma.
 * 持有圣徒的玩家技能状态，以及 UUID 业障在客户端可见的镜像。
 */
public final class SaintPlayerState {
    private int hellfireCooldownTicks;
    private int hellfireActiveTicks;
    private boolean karmaMarked;
    private int karmaCooldownTicks;

    public int hellfireCooldownTicks() {
        return hellfireCooldownTicks;
    }

    public int hellfireActiveTicks() {
        return hellfireActiveTicks;
    }

    public boolean isHellfireActive() {
        return hellfireActiveTicks > 0;
    }

    public boolean isKarmaMarked() {
        return karmaMarked;
    }

    public int karmaCooldownTicks() {
        return karmaCooldownTicks;
    }

    public boolean initializeAbility() {
        boolean changed = hellfireCooldownTicks != SaintRules.HELLFIRE_INITIAL_COOLDOWN_TICKS
                || hellfireActiveTicks != 0;
        hellfireCooldownTicks = SaintRules.HELLFIRE_INITIAL_COOLDOWN_TICKS;
        hellfireActiveTicks = 0;
        return changed;
    }

    public void activateHellfire() {
        hellfireCooldownTicks = 0;
        hellfireActiveTicks = SaintRules.HELLFIRE_ACTIVE_TICKS;
    }

    public boolean clearHellfire() {
        if (hellfireActiveTicks <= 0) {
            return false;
        }
        hellfireActiveTicks = 0;
        return true;
    }

    public boolean clearAbility() {
        if (hellfireCooldownTicks == 0 && hellfireActiveTicks == 0) {
            return false;
        }
        hellfireCooldownTicks = 0;
        hellfireActiveTicks = 0;
        return true;
    }

    /**
     * Advances Hellfire and reports whether the owner component should sync this tick.
     * 推进业火计时，并返回本 tick 是否需要由所属组件同步。
     */
    public boolean tickAbility() {
        if (hellfireActiveTicks > 0) {
            hellfireActiveTicks--;
            if (hellfireActiveTicks == 0) {
                hellfireCooldownTicks = SaintRules.HELLFIRE_POST_COOLDOWN_TICKS;
                return true;
            }
            return hellfireActiveTicks % 20 == 0;
        }
        if (hellfireCooldownTicks <= 0) {
            return false;
        }
        hellfireCooldownTicks--;
        return hellfireCooldownTicks == 0 || hellfireCooldownTicks % 20 == 0;
    }

    public boolean updateKarma(boolean marked, int remainingTicks) {
        int normalizedTicks = Math.max(0, remainingTicks);
        if (karmaMarked == marked && karmaCooldownTicks == normalizedTicks) {
            return false;
        }
        boolean shouldSync = karmaMarked != marked
                || normalizedTicks == 0
                || normalizedTicks % 20 == 0
                || Math.abs(karmaCooldownTicks - normalizedTicks) > 1;
        karmaMarked = marked;
        karmaCooldownTicks = normalizedTicks;
        return shouldSync;
    }

    public boolean isEmpty() {
        return hellfireCooldownTicks == 0
                && hellfireActiveTicks == 0
                && !karmaMarked
                && karmaCooldownTicks == 0;
    }

    public void clear() {
        hellfireCooldownTicks = 0;
        hellfireActiveTicks = 0;
        karmaMarked = false;
        karmaCooldownTicks = 0;
    }

    /**
     * Keeps the established Saint NBT keys stable while the in-memory owner is split out.
     * 拆分内存状态时仍保持既有圣徒 NBT 键不变。
     */
    public void writeNbt(NbtCompound tag) {
        if (hellfireCooldownTicks > 0) {
            tag.putInt("SaintHellfireCooldownTicks", hellfireCooldownTicks);
        }
        if (hellfireActiveTicks > 0) {
            tag.putInt("SaintHellfireActiveTicks", hellfireActiveTicks);
        }
        if (karmaMarked) {
            tag.putBoolean("SaintKarmaMarked", true);
            if (karmaCooldownTicks > 0) {
                tag.putInt("SaintKarmaCooldownTicks", karmaCooldownTicks);
            }
        }
    }

    public void readNbt(NbtCompound tag) {
        hellfireCooldownTicks = tag.contains("SaintHellfireCooldownTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("SaintHellfireCooldownTicks"))
                : 0;
        hellfireActiveTicks = tag.contains("SaintHellfireActiveTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("SaintHellfireActiveTicks"))
                : 0;
        karmaMarked = tag.getBoolean("SaintKarmaMarked");
        karmaCooldownTicks = karmaMarked && tag.contains("SaintKarmaCooldownTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("SaintKarmaCooldownTicks"))
                : 0;
    }

    /**
     * These four fields remain one contiguous packet segment in their original order.
     * 这四个字段继续按原顺序占据同步包中的同一连续区段。
     */
    public void writeSync(RegistryByteBuf buf, boolean ownerVisible) {
        buf.writeVarInt(ownerVisible ? hellfireCooldownTicks : 0);
        buf.writeVarInt(ownerVisible ? hellfireActiveTicks : 0);
        buf.writeBoolean(ownerVisible && karmaMarked);
        buf.writeVarInt(ownerVisible ? karmaCooldownTicks : 0);
    }

    public void readSync(RegistryByteBuf buf) {
        hellfireCooldownTicks = Math.max(0, buf.readVarInt());
        hellfireActiveTicks = Math.max(0, buf.readVarInt());
        karmaMarked = buf.readBoolean();
        karmaCooldownTicks = Math.max(0, buf.readVarInt());
    }
}
