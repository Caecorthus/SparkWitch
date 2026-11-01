package dev.caecorthus.sparkwitch.roles.civilian.recruit;

import dev.caecorthus.sparkfactionapi.api.FactionIds;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.index.tag.WatheItemTags;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Pure Recruit constants and predicates. The Recruit is an ordinary task-funded passenger whose sanity bar, stamina
 * and tasks come from the civilian role definition; after one completed task it earns Wathe's knife.
 * 新兵的纯常数与判断。新兵是普通任务平民，理智条、体力与任务来自平民职业定义；完成第一个任务后获得 Wathe 的刀。
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

    /** Whether the Recruit carries Wathe's knife (main inventory or offhand). / 新兵是否带着 Wathe 的刀（主物品栏或副手）。 */
    public static boolean hasKnife(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        for (ItemStack stack : player.getInventory().main) {
            if (stack.isOf(WatheItems.KNIFE)) {
                return true;
            }
        }
        for (ItemStack stack : player.getInventory().offHand) {
            if (stack.isOf(WatheItems.KNIFE)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the player carries any {@code wathe:guns} item (main inventory or offhand). / 是否带着任意 {@code wathe:guns}。 */
    public static boolean hasGun(@Nullable PlayerEntity player) {
        if (player == null) {
            return false;
        }
        for (ItemStack stack : player.getInventory().main) {
            if (stack.isIn(WatheItemTags.GUNS)) {
                return true;
            }
        }
        for (ItemStack stack : player.getInventory().offHand) {
            if (stack.isIn(WatheItemTags.GUNS)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether this player belongs to the Recruit's separate ground gun-pickup whitelist: the Recruit role and a
     * carried Wathe knife. Once a gun has been acquired this round the component's flag blocks every further pickup.
     * Wathe otherwise blocks an innocent player without a gun or leftover Veteran stabs; the tick whitelist bypasses
     * that only for the Recruit.
     * 此玩家是否属于新兵独有的地面枪械拾取白名单：新兵职业且身上带着 Wathe 的刀。本局一旦拿到枪，组件标志会阻止继续拾取。
     * Wathe 原本会拦下没有枪、也没有老兵剩余刺击次数的好人；tick 白名单仅对新兵绕过该规则。
     */
    public static boolean mayPickUpGuns(PlayerEntity player) {
        if (player == null || player.getWorld() == null) {
            return false;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        return isRecruit(game.getRole(player))
                && hasKnife(player)
                && !RecruitPlayerComponent.KEY.get(player).isGunAcquired();
    }
}
