package dev.caecorthus.sparkwitch.compat;

import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianDecoyBodies;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Owner decision 2026-10-07 D2: NoellesRoles' Vulture cannot eat a Magician decoy body. The Vulture eat receiver looks
 * the requested body up exactly once, after its own role, life, Taotie and cooldown gates; this guard removes decoys
 * from that lookup, so NoellesRoles takes its own "no body" path (no cooldown, eaten count, speed, highlight, discard
 * or replay record) and only that Vulture is told why, on the action bar. Decoy status stays server-only: the Vulture's
 * client still outlines and prompts the decoy like any body and learns nothing beyond this refusal.
 * 所有者 2026-10-07 决定 D2：NoellesRoles 的秃鹫不能吃魔术师的诱饵尸体。秃鹫进食接收器在通过自身的职业、存活、饕餮与冷却
 * 检查后只查找一次目标尸体；本守卫从这次查找结果中移除诱饵，使 NoellesRoles 走自己的“没有尸体”分支（不进冷却、不计数、
 * 不加速、不透视、不移除尸体、不记回放），并只在动作栏告诉该秃鹫原因。诱饵身份只在服务端：秃鹫客户端仍像对待普通尸体一样
 * 描边并提示该诱饵，除这条拒绝提示外得不到任何信息。
 */
public final class NoellesVultureDecoyGuard {
    public static final String DECOY_BODY_MESSAGE = "message.sparkwitch.vulture.decoy_body";

    private NoellesVultureDecoyGuard() {
    }

    /** Server receiver entry point. / 服务端接收器入口。 */
    public static <T extends Entity> List<T> withoutDecoys(ServerPlayerEntity vulture, List<T> found) {
        return withoutDecoys(found, MagicianDecoyBodies::isDecoy,
                () -> vulture.sendMessage(Text.translatable(DECOY_BODY_MESSAGE), true));
    }

    /**
     * Drops decoys; {@code onRefused} runs once when decoys were the only matches. Returns {@code found} itself when it
     * holds no decoy.
     * 移除诱饵；若匹配结果全部是诱饵，则执行一次 {@code onRefused}。没有诱饵时原样返回 {@code found}。
     */
    static <T> List<T> withoutDecoys(List<T> found, Predicate<? super T> isDecoy, Runnable onRefused) {
        if (found.isEmpty()) {
            return found;
        }
        List<T> kept = new ArrayList<>(found.size());
        for (T candidate : found) {
            if (!isDecoy.test(candidate)) {
                kept.add(candidate);
            }
        }
        if (kept.size() == found.size()) {
            return found;
        }
        if (kept.isEmpty()) {
            onRefused.run();
        }
        return kept;
    }
}
