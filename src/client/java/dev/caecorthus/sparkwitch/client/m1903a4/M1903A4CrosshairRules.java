package dev.caecorthus.sparkwitch.client.m1903a4;

import dev.caecorthus.sparkwitch.util.hitscan.HitscanLagRules;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * Pure, world-free decisions behind the M1903A4 target crosshair; {@link M1903A4CrosshairTargeting} only gathers the
 * inputs. Candidates pass public state only, so the hint never tells a hidden role, faction or trait veto apart; the
 * server decides every shot.
 * M1903A4 目标准星背后的纯逻辑判定，不访问世界；{@link M1903A4CrosshairTargeting} 只负责收集输入。候选者只按公开状态
 * 筛选，因此提示无法区分隐藏的职业、阵营或词条否决；每一枪都由服务端判定。
 */
public final class M1903A4CrosshairRules {
    private M1903A4CrosshairRules() {
    }

    /**
     * Whether the hint may light at all: the rifle sits in the main hand, the player is charging it with that hand (the
     * rifle fires from the main hand only), and no cooldown would stop the release from firing.
     * 提示是否可以点亮：步枪在主手、玩家正用主手蓄力（步枪只能主手开火），且没有会让松手无法开火的冷却。
     */
    public static boolean charging(boolean rifleInMainHand, boolean usingMainHand, boolean coolingDown) {
        return rifleInMainHand && usingMainHand && !coolingDown;
    }

    /**
     * A player that may light the hint: Wathe's own gun-crosshair filter (alive, not spectator or creative, not
     * invisible), never the shooter and never an active Wraith (public synced state; the server never hits one).
     * 可点亮提示的玩家：Wathe 自身枪械准星的过滤（存活、非旁观或创造、未隐身），绝不是射手本人，也绝不是激活的冤魂（已同步的
     * 公开状态；服务端从不命中激活冤魂）。
     */
    public static boolean playerCandidate(boolean self, boolean alive, boolean aliveAndSurvival, boolean invisible,
                                          boolean activeWraith) {
        return !self && alive && aliveAndSurvival && !invisible && !activeWraith;
    }

    /**
     * A live, visible Magician puppet lights the hint as its copied player would; leaving it dark would tell a puppet
     * from a real player. / 存活且可见的魔术师皮套与其复制的玩家一样点亮提示；若皮套不亮，就能把皮套与真玩家区分开。
     */
    public static boolean puppetCandidate(boolean alive, boolean removed, boolean invisible) {
        return alive && !removed && !invisible;
    }

    /**
     * The candidate whose box the ray {@code eye..end} (already cut at the first block) enters first; a box holding the
     * eye counts as distance 0. {@code null} when the ray enters none.
     * 射线 {@code eye..end}（已在第一个方块处截断）最先进入其箱体的候选者；包含眼睛的箱体距离记为 0。射线未进入任何箱体时为
     * {@code null}。
     */
    public static <T> @Nullable T nearest(Vec3d eye, Vec3d end, List<T> candidates, Function<T, Box> box) {
        T nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (T candidate : candidates) {
            double distance = HitscanLagRules.entryDistanceSquared(eye, end, List.of(box.apply(candidate)));
            if (distance >= 0.0 && distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }
}
