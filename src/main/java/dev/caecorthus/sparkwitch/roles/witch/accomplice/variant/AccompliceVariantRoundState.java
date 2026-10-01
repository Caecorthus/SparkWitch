package dev.caecorthus.sparkwitch.roles.witch.accomplice.variant;

import dev.doctor4t.wathe.api.Role;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Special accomplices already used this round, by role id, independent of component registration. Recorded when a
 * recruitment into a variant commits and seeded at round start from the role map, so a variant that later dies and
 * becomes a Curser stays used. NBT: {@code UsedVariants} = list of role id strings.
 * 本局已使用的特殊共犯（按职业 id），不依赖组件注册。招募为特殊共犯提交时记录，开局时按身份表预置，
 * 因此之后死亡并转为诅咒者的特殊共犯仍视为已使用。NBT：{@code UsedVariants} 为职业 id 字符串列表。
 */
public class AccompliceVariantRoundState {
    static final String USED_VARIANTS = "UsedVariants";

    private final Set<Identifier> used = new LinkedHashSet<>();

    /** Starts a round with the variants already in the role map. / 以身份表中已有的特殊共犯开始新一局。 */
    public void beginRound(Iterable<Role> alreadyAssigned) {
        used.clear();
        for (Role role : alreadyAssigned) {
            markUsed(role);
        }
    }

    public void clearRound() {
        used.clear();
    }

    public void markUsed(Role role) {
        if (role != null) {
            used.add(role.identifier());
        }
    }

    public boolean isUsed(Role role) {
        return role != null && used.contains(role.identifier());
    }

    public Set<Identifier> usedIds() {
        return Set.copyOf(used);
    }

    public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        NbtList list = new NbtList();
        for (Identifier id : used) {
            list.add(NbtString.of(id.toString()));
        }
        tag.put(USED_VARIANTS, list);
    }

    public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup lookup) {
        used.clear();
        for (NbtElement element : tag.getList(USED_VARIANTS, NbtElement.STRING_TYPE)) {
            Identifier id = Identifier.tryParse(element.asString());
            if (id != null) {
                used.add(id);
            }
        }
    }
}
