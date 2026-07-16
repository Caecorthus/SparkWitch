package dev.caecorthus.sparkwitch.api;

import net.minecraft.util.Identifier;
import net.minecraft.entity.player.PlayerEntity;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToIntFunction;

public final class WitchSkillRegistry {
    private static final Map<Identifier, WitchSkillDefinition> SKILLS = new LinkedHashMap<>();
    private static final Map<Identifier, ToIntFunction<PlayerEntity>> ACTIVE_WINDOW_PROVIDERS = new LinkedHashMap<>();

    private WitchSkillRegistry() {
    }

    public static WitchSkillDefinition register(WitchSkillDefinition definition) {
        return register(definition, null);
    }

    /**
     * Registers an optional role-owned active-window query without changing the skill ABI or player schema.
     * 注册可选的角色自有活动窗口查询，不改变技能 ABI 或玩家 schema。
     */
    public static WitchSkillDefinition register(
            WitchSkillDefinition definition,
            ToIntFunction<PlayerEntity> activeWindowProvider
    ) {
        if (SKILLS.containsKey(definition.id())) {
            throw new IllegalArgumentException("Witch skill already registered: " + definition.id());
        }
        SKILLS.put(definition.id(), definition);
        if (activeWindowProvider != null) {
            ACTIVE_WINDOW_PROVIDERS.put(definition.id(), activeWindowProvider);
        }
        return definition;
    }

    public static int activeWindowTicks(Identifier skillId, PlayerEntity player) {
        if (skillId == null || player == null) {
            return 0;
        }
        ToIntFunction<PlayerEntity> provider = ACTIVE_WINDOW_PROVIDERS.get(skillId);
        return provider == null ? 0 : Math.max(0, provider.applyAsInt(player));
    }

    public static WitchSkillDefinition get(Identifier id) {
        return SKILLS.get(id);
    }

    public static Collection<WitchSkillDefinition> values() {
        return java.util.List.copyOf(SKILLS.values());
    }

}
