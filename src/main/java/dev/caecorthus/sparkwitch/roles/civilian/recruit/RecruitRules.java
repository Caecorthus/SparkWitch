package dev.caecorthus.sparkwitch.roles.civilian.recruit;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.tag.WatheItemTags;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Pure Recruit constants and predicates. The Recruit is an ordinary task passenger (no coins or shop) whose sanity
 * bar, stamina and tasks come from the civilian role definition; after one completed task it earns Wathe's knife.
 * 新兵的纯常数与判断。新兵是只做任务的普通平民（没有金币与商店），理智条、体力与任务来自平民职业定义；完成第一个任务后
 * 获得 Wathe 的刀。
 */
public final class RecruitRules {
    public static final Identifier ROLE_ID = SparkWitch.id("recruit");
    /**
     * Slightly lighter than Wathe's Veteran ({@code 0x4A7023}) and darker than the Orthopedist ({@code 0x90B358}).
     * 比 Wathe 老兵（{@code 0x4A7023}）稍浅，比骨科大夫（{@code 0x90B358}）深。
     */
    public static final int ROLE_COLOR = 0x5B862D;
    /** The first completed task grants the knife. / 完成第一个任务即发刀。 */
    public static final int REQUIRED_TASKS = 1;
    /** Mood (sanity points / 100) lost for every knife kill. / 每次用刀击杀损失的理智（理智点 / 100）。 */
    public static final float KNIFE_KILL_MOOD_COST = 0.35F;

    private RecruitRules() {
    }

    public static boolean isRecruit(@Nullable Role role) {
        return role != null && ROLE_ID.equals(role.identifier());
    }

    /**
     * "Good" for the Recruit's innocent-kill rule: SparkFactionAPI's effective faction is CIVILIAN, which folds
     * Impostor out and Conscience in. Neutrals are not good.
     * 新兵误杀规则中的“好人”：SparkFactionAPI 有效阵营为 CIVILIAN，内鬼已排除、善良已计入。中立不算好人。
     */
    public static boolean isGood(@Nullable PlayerEntity player, GameWorldComponent game) {
        return player != null && FactionIds.CIVILIAN.equals(SparkFactionApi.resolveEffectiveFaction(player, game));
    }

    /**
     * Ground pickup veto: a live Recruit who already picked up one {@code wathe:guns} item this round picks up no
     * other. Wathe's own rule (an innocent with no gun, not its own throw, no misfire ban) still decides the first
     * pickup; server side only, creative players are free, the role is asked last.
     * 地面拾取否决：本局已从地面捡过一把 {@code wathe:guns} 的新兵不能再捡。第一把是否能捡仍由 Wathe 自身规则决定
     * （没有枪的好人、不是自己丢出的枪、未受误杀禁捡）；仅服务端，创造模式不受限，职业最后才询问。
     */
    public static boolean blocksGunPickup(@Nullable PlayerEntity player, @Nullable ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty() || player.getWorld() == null
                || player.getWorld().isClient || player.isCreative() || !stack.isIn(WatheItemTags.GUNS)) {
            return false;
        }
        return isRecruit(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))
                && RecruitPlayerComponent.KEY.get(player).hasPickedUpGun();
    }
}
