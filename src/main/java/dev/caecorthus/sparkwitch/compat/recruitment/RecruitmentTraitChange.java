package dev.caecorthus.sparkwitch.compat.recruitment;

import net.minecraft.text.Text;

import java.util.List;

/** What the SparkTraits swap did to one recruit: the lost trait names the recruit could see and the visible redraws,
 * each in order. {@link #NONE} when nothing changed or the facade is missing.
 * SparkTraits 词条替换对被招募者的结果：本人可见的失去词条名与可见的补抽词条名，均保持原顺序。无变化或门面缺失时为
 * {@link #NONE}。 */
public record RecruitmentTraitChange(List<Text> lost, List<Text> gained) {
    public static final RecruitmentTraitChange NONE = new RecruitmentTraitChange(List.of(), List.of());

    public RecruitmentTraitChange {
        lost = List.copyOf(lost);
        gained = List.copyOf(gained);
    }
}
