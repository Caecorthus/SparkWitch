package dev.caecorthus.sparkwitch.component;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Pure NBT helpers for next-round forced witch ability locks.
 * 下一局强制魔女能力锁定的纯 NBT 工具，避免普通单元测试初始化 CCA。
 */
final class WitchForcedSkillState {
    private static final String FORCED_SKILL_KEY = "ForcedSkill";

    private WitchForcedSkillState() {
    }

    static void writeToNbt(NbtCompound tag, @Nullable Identifier forcedSkillId) {
        if (forcedSkillId != null) {
            tag.putString(FORCED_SKILL_KEY, forcedSkillId.toString());
        }
    }

    static @Nullable Identifier readFromNbt(NbtCompound tag) {
        return tag.contains(FORCED_SKILL_KEY, NbtElement.STRING_TYPE)
                ? Identifier.tryParse(tag.getString(FORCED_SKILL_KEY))
                : null;
    }
}
