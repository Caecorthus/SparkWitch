package dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Server-only stash of one identity that is not live: slot-indexed stacks (never CUSTOM_DATA tags),
 * overflow that must never be dropped, per-identity ability/skill cooldown deadlines, and its own wallet.
 * 单个非当前身份的服务端存档：按槽位记录的物品（从不使用 CUSTOM_DATA 标记）、绝不丢弃的溢出物品、
 * 该身份的能力/技能冷却截止时间，以及独立钱包。
 */
public final class BlackRavenIdentityStash {
    /**
     * Overflow decode cap: MAX_STASH_OVERFLOW (16) plus a full inventory, so a save/load never loses a stack
     * that a swap kept (decision D10). Runtime overflow itself is never truncated.
     * 溢出解码上限：MAX_STASH_OVERFLOW（16）加一整个背包，确保存读档不会丢失交换保留的物品（决策 D10）；
     * 运行时溢出本身从不截断。
     */
    static final int OVERFLOW_DECODE_CAP =
            BlackRavenDisguiseRules.MAX_STASH_OVERFLOW + BlackRavenDisguiseRules.PLAYER_SLOT_COUNT;

    private final List<SlotStack> slots = new ArrayList<>();
    private final List<ItemStack> overflow = new ArrayList<>();
    private long abilityCooldownUntil;
    private @Nullable Identifier skillId;
    private long skillCooldownUntil;
    private int balance;

    /** One stashed stack and the player-inventory slot (0-40) it came from. / 一个存档物品及其来源槽位（0-40）。 */
    public record SlotStack(int slot, ItemStack stack) {
        public SlotStack {
            Objects.requireNonNull(stack, "stack");
        }
    }

    public List<SlotStack> slots() {
        return slots;
    }

    public List<ItemStack> overflow() {
        return overflow;
    }

    public long abilityCooldownUntil() {
        return abilityCooldownUntil;
    }

    public void setAbilityCooldownUntil(long abilityCooldownUntil) {
        this.abilityCooldownUntil = abilityCooldownUntil;
    }

    public @Nullable Identifier skillId() {
        return skillId;
    }

    public void setSkillId(@Nullable Identifier skillId) {
        this.skillId = skillId;
    }

    public long skillCooldownUntil() {
        return skillCooldownUntil;
    }

    public void setSkillCooldownUntil(long skillCooldownUntil) {
        this.skillCooldownUntil = skillCooldownUntil;
    }

    public int balance() {
        return balance;
    }

    public void setBalance(int balance) {
        this.balance = balance;
    }

    public boolean hasItems() {
        return !slots.isEmpty() || !overflow.isEmpty();
    }

    public NbtCompound toNbt(RegistryWrapper.WrapperLookup lookup) {
        NbtCompound nbt = new NbtCompound();
        NbtList slotList = new NbtList();
        for (SlotStack entry : slots) {
            if (entry.stack().isEmpty()) {
                continue;
            }
            NbtCompound slotNbt = new NbtCompound();
            slotNbt.putByte(BlackRavenDisguiseState.KEY_STASH_SLOT, (byte) entry.slot());
            slotNbt.put(BlackRavenDisguiseState.KEY_STASH_ITEM, entry.stack().encode(lookup));
            slotList.add(slotNbt);
        }
        nbt.put(BlackRavenDisguiseState.KEY_STASH_SLOTS, slotList);
        NbtList overflowList = new NbtList();
        for (ItemStack stack : overflow) {
            if (!stack.isEmpty()) {
                overflowList.add(stack.encode(lookup));
            }
        }
        nbt.put(BlackRavenDisguiseState.KEY_STASH_OVERFLOW, overflowList);
        nbt.putLong(BlackRavenDisguiseState.KEY_STASH_ABILITY_CD_UNTIL, abilityCooldownUntil);
        if (skillId != null) {
            nbt.putString(BlackRavenDisguiseState.KEY_STASH_SKILL_ID, skillId.toString());
        }
        nbt.putLong(BlackRavenDisguiseState.KEY_STASH_SKILL_CD_UNTIL, skillCooldownUntil);
        nbt.putInt(BlackRavenDisguiseState.KEY_STASH_BALANCE, balance);
        return nbt;
    }

    /** Skips invalid stacks and out-of-range slots; applies the decode caps. / 跳过无效物品与越界槽位，并应用解码上限。 */
    public static BlackRavenIdentityStash fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        BlackRavenIdentityStash stash = new BlackRavenIdentityStash();
        NbtList slotList = nbt.getList(BlackRavenDisguiseState.KEY_STASH_SLOTS, NbtElement.COMPOUND_TYPE);
        for (int index = 0; index < slotList.size()
                && stash.slots.size() < BlackRavenDisguiseRules.MAX_STASH_SLOT_STACKS; index++) {
            NbtCompound slotNbt = slotList.getCompound(index);
            int slot = slotNbt.getByte(BlackRavenDisguiseState.KEY_STASH_SLOT);
            if (slot < 0 || slot >= BlackRavenDisguiseRules.PLAYER_SLOT_COUNT
                    || !slotNbt.contains(BlackRavenDisguiseState.KEY_STASH_ITEM, NbtElement.COMPOUND_TYPE)) {
                continue;
            }
            decode(lookup, slotNbt.get(BlackRavenDisguiseState.KEY_STASH_ITEM))
                    .ifPresent(stack -> stash.slots.add(new SlotStack(slot, stack)));
        }
        NbtList overflowList = nbt.getList(BlackRavenDisguiseState.KEY_STASH_OVERFLOW, NbtElement.COMPOUND_TYPE);
        for (int index = 0; index < overflowList.size() && stash.overflow.size() < OVERFLOW_DECODE_CAP; index++) {
            decode(lookup, overflowList.getCompound(index)).ifPresent(stash.overflow::add);
        }
        stash.abilityCooldownUntil = nbt.getLong(BlackRavenDisguiseState.KEY_STASH_ABILITY_CD_UNTIL);
        stash.skillId = nbt.contains(BlackRavenDisguiseState.KEY_STASH_SKILL_ID, NbtElement.STRING_TYPE)
                ? BlackRavenDisguiseState.parseRoleId(nbt.getString(BlackRavenDisguiseState.KEY_STASH_SKILL_ID))
                : null;
        stash.skillCooldownUntil = nbt.getLong(BlackRavenDisguiseState.KEY_STASH_SKILL_CD_UNTIL);
        stash.balance = Math.max(0, nbt.getInt(BlackRavenDisguiseState.KEY_STASH_BALANCE));
        return stash;
    }

    private static Optional<ItemStack> decode(RegistryWrapper.WrapperLookup lookup, @Nullable NbtElement element) {
        if (element == null) {
            return Optional.empty();
        }
        try {
            return ItemStack.fromNbt(lookup, element).filter(stack -> !stack.isEmpty());
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}
