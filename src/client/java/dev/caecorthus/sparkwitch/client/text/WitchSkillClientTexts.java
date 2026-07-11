package dev.caecorthus.sparkwitch.client.text;

import dev.caecorthus.sparkwitch.api.WitchSkillDefinition;
import dev.caecorthus.sparkwitch.api.WitchSkillRegistry;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.GrandWitchRules;
import dev.caecorthus.sparkwitch.skill.WitchSkillHudRules;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class WitchSkillClientTexts {
    private static final int UNKNOWN_SKILL_COLOR = 0xAAAAAA;

    private WitchSkillClientTexts() {
    }

    public static int color(Identifier skillId) {
        WitchSkillDefinition skill = WitchSkillRegistry.get(skillId);
        return skill == null ? UNKNOWN_SKILL_COLOR : skill.color();
    }

    public static MutableText name(Identifier skillId) {
        WitchSkillDefinition skill = WitchSkillRegistry.get(skillId);
        return skill == null ? Text.literal(skillId.toString()) : skill.name();
    }

    public static MutableText tag(Identifier skillId) {
        return Text.literal("[")
                .append(name(skillId))
                .append(Text.literal("]"));
    }

    public static List<Text> tooltip(Identifier skillId, int cooldownTicks) {
        return tooltip(skillId, cooldownTicks, 0, 0);
    }

    public static List<Text> tooltip(Identifier skillId, int cooldownTicks, int activeTicks) {
        return tooltip(skillId, cooldownTicks, activeTicks, 0);
    }

    public static List<Text> tooltip(Identifier skillId, int cooldownTicks, int activeTicks, int ceremonialSwordTasks) {
        WitchSkillDefinition skill = WitchSkillRegistry.get(skillId);
        List<Text> lines = new ArrayList<>();
        if (skill == null) {
            lines.add(Text.literal(skillId.toString()));
        } else {
            lines.add(skill.name());
            lines.add(skill.description());
        }
        if (activeTicks > 0) {
            lines.add(Text.translatable("gui.sparkwitch.skill.active", (int) Math.ceil(activeTicks / 20.0)));
        } else if (WitchSkillHudRules.shouldShowCeremonialSwordTaskUnlock(
                skillId,
                ceremonialSwordTasks,
                activeTicks,
                cooldownTicks
        )) {
            lines.add(Text.translatable(
                    "gui.sparkwitch.skill.ceremonial_sword.locked",
                    ceremonialSwordTasks,
                    GrandWitchRules.CEREMONIAL_SWORD_UNLOCK_TASKS
            ));
        } else if (cooldownTicks > 0) {
            lines.add(Text.translatable("gui.sparkwitch.skill.cooldown", (int) Math.ceil(cooldownTicks / 20.0)));
        } else {
            lines.add(Text.translatable("gui.sparkwitch.skill.ready"));
        }
        return lines;
    }
}
