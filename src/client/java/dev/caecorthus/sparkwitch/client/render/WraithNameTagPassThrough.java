package dev.caecorthus.sparkwitch.client.render;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.function.Predicate;

/**
 * Lets name-tag raycasts pass through active Wraiths that the viewer is not allowed to see.
 * 让名牌射线穿过观察者本不该看见的激活冤魂。
 *
 * <p>Wathe's {@code RoleNameRenderer} and the SparkWitch labels drawn on top of it pick their target with an
 * unscoped {@code instanceof PlayerEntity} raycast. An invisible active Wraith in front of the viewer is picked
 * first, so its name reveals its position and the player behind it is never labelled. This is a presentation
 * rule, not the aim rule: whoever may see the Wraith through {@link WraithViewerRules} (spectators, killers for
 * the promoted Saboteur, the witch faction for the promoted Curser, the bound killer for its Vendetta) still
 * selects it, so their intentional name and cohort labels keep working; without the Curser case Wathe's name-tag
 * fade would also hide the witch cohort label drawn for it.
 * Wathe 的 RoleNameRenderer 及叠加其上的 SparkWitch 标签都用不区分对象的 instanceof PlayerEntity 射线选目标；
 * 身前隐身的激活冤魂会先被选中，名字暴露其位置，身后的玩家反而没有名牌。这是显示规则而非瞄准规则：
 * 按 WraithViewerRules 可见冤魂的一方（旁观者、看晋升破坏者的杀手、看晋升诅咒者的魔女阵营、看仇杀客的绑定凶手）
 * 仍会选中它，原有的名字与同伙标签保持不变；若没有诅咒者这一项，Wathe 名牌淡出会连带隐藏为其绘制的魔女同伙标签。</p>
 *
 * <p>Only the synced Wraith state counts: invisibility and {@code noellesroles:no_collision} also appear on
 * living players. 只认同步的冤魂状态：隐身与 no_collision 也会出现在存活玩家身上。</p>
 */
public final class WraithNameTagPassThrough {
    private WraithNameTagPassThrough() {
    }

    /** Narrows a name-tag raycast predicate for this viewer; it never widens it. / 仅收窄该观察者的名牌射线判定，从不放宽。 */
    public static Predicate<Entity> filterNameTarget(Entity viewer, Predicate<Entity> predicate) {
        if (!(viewer instanceof PlayerEntity player)) {
            return predicate;
        }
        return candidate -> predicate.test(candidate) && !passesThrough(player, candidate);
    }

    static boolean passesThrough(PlayerEntity viewer, Entity candidate) {
        return candidate instanceof PlayerEntity target
                && WraithViewerRules.shouldHideFromOrdinaryViewer(viewer, target);
    }
}
