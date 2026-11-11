package dev.caecorthus.sparkwitch.client.sherry;

import dev.caecorthus.sparkwitch.roles.civilian.hanna.HannaRules;
import dev.caecorthus.sparkwitch.roles.civilian.sherry.SherryComponent;
import dev.caecorthus.sparkwitch.roles.civilian.sherry.SherryRules;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.UUID;

/**
 * Two client-side reveals that never touch the server's glowing flag:
 * <ul>
 *   <li>Sherry sees the target she deduced, in her own colour, for five seconds at a time.</li>
 *   <li>Sherry and 汉娜 see each other, because both are looking for the same person.</li>
 * </ul>
 * 两条纯客户端的揭示，不动服务端的发光标记：
 * <ul>
 *   <li>雪莉能看到自己推演出的目标，用自己的颜色，每次五秒；</li>
 *   <li>雪莉与汉娜互相可见，因为两人在找同一个人。</li>
 * </ul>
 */
public final class SherryInstinctHooks {

    private static final int SHERRY_COLOR = 0xFF65D0EE;
    private static final int PRIORITY = GetInstinctHighlight.HighlightResult.PRIORITY_DEFAULT + 80;
    private static boolean registered;

    private static UUID visionTarget;
    private static long visionUntil;

    private SherryInstinctHooks() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GetInstinctHighlight.EVENT.register(SherryInstinctHooks::highlight);
    }

    /** Keep the local reveal window alive; the server refreshes it every 20 s / 40 s. */
    /** 维护本地揭示窗口；服务端每 20/40 秒刷新一次。 */
    public static void tick() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return;
        }
        SherryComponent component = SherryComponent.KEY.get(player);
        UUID target = component.getVisionTarget();
        if (target == null || component.getVisionTicks() <= 0) {
            visionTarget = null;
            return;
        }
        if (!target.equals(visionTarget) || component.getVisionTicks() > SherryRules.VISION_TICKS) {
            return;
        }
        // A fresh window arrived (the server always writes a full length): restart the local countdown.
        // 收到新窗口（服务端总是写满长度）：重启本地倒计时。
        if (component.getVisionTicks() >= SherryRules.VISION_TICKS && !target.equals(visionTarget)) {
            visionTarget = target;
            visionUntil = player.getWorld().getTime() + SherryRules.VISION_TICKS;
        } else if (target.equals(visionTarget) && visionUntil == 0L) {
            visionUntil = player.getWorld().getTime() + SherryRules.VISION_TICKS;
        }
    }

    private static GetInstinctHighlight.HighlightResult highlight(Entity target) {
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null || !(target instanceof PlayerEntity other) || other == viewer) {
            return null;
        }
        // Sherry <-> 汉娜: mutual reveal. 雪莉与汉娜互相可见。
        var viewerRole = GameWorldComponent.KEY.get(viewer.getWorld()).getRole(viewer);
        var otherRole = GameWorldComponent.KEY.get(other.getWorld()).getRole(other);
        if (viewerRole != null && otherRole != null) {
            boolean viewerSherry = SherryRules.ROLE_ID.equals(viewerRole.identifier());
            boolean viewerHanna = HannaRules.ROLE_ID.equals(viewerRole.identifier());
            boolean otherSherry = SherryRules.ROLE_ID.equals(otherRole.identifier());
            boolean otherHanna = HannaRules.ROLE_ID.equals(otherRole.identifier());
            if ((viewerSherry && otherHanna) || (viewerHanna && otherSherry)) {
                return new GetInstinctHighlight.HighlightResult(SHERRY_COLOR, false, PRIORITY);
            }
        }
        // Sherry's own deduction window, private to her client. 雪莉自己的推演窗口，仅她的客户端可见。
        if (viewerRole == null || !SherryRules.ROLE_ID.equals(viewerRole.identifier())) {
            return null;
        }
        if (visionTarget == null || !other.getUuid().equals(visionTarget)) {
            return null;
        }
        if (viewer.getWorld().getTime() > visionUntil) {
            return null;
        }
        return new GetInstinctHighlight.HighlightResult(SHERRY_COLOR, false, PRIORITY);
    }
}
