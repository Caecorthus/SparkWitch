package dev.caecorthus.sparkwitch.client.medium;

import dev.caecorthus.sparkwitch.compat.NoellesHiddenBodiesBridge;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.medium.MediumRules;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * 尸体透视 from 幽灵的加护: every REAL corpse that existed when the blessing was granted (the server's owner-synced,
 * round-long set) stays outlined through walls in the role's ghost-fire teal, steady and independent of the instinct
 * key, for the living Medium alone. A NoellesRoles-hidden body stays hidden, and a despawned body simply stops
 * rendering. The priority equals the Charon's body marks ({@code PRIORITY_DEFAULT + 40}), so the Specter veil, the
 * Fisher / SparkStrength hides and every {@code skip()} still win.
 * 幽灵的加护的尸体透视：获得加护时已存在的每具「真实」尸体（服务端仅同步给本人、整局有效的集合）以职业的鬼火青绿常亮穿墙描边，
 * 不闪烁、不依赖本能键，只对存活的通灵师本人显示。被 NoellesRoles 隐藏的尸体保持隐藏，尸体实体消失后自然不再显示。优先级与卡戎的
 * 尸体标记相同（{@code PRIORITY_DEFAULT + 40}），因此幽魂帷幕、钓鱼佬 / SparkStrength 的隐藏以及所有 {@code skip()} 仍然优先。
 */
public final class MediumCorpseSightHighlights {
    /** The role colour, without alpha (Wathe outline colours are RGB). / 职业色，不含透明度（Wathe 描边颜色为 RGB）。 */
    static final int COLOR = MediumRules.COLOR;
    static final int PRIORITY = GetInstinctHighlight.HighlightResult.PRIORITY_DEFAULT + 40;
    private static boolean registered;

    private MediumCorpseSightHighlights() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GetInstinctHighlight.EVENT.register(MediumCorpseSightHighlights::highlight);
    }

    @Nullable
    private static GetInstinctHighlight.HighlightResult highlight(Entity target) {
        if (!SparkWitchServerConnection.isConfirmedServer() || !(target instanceof PlayerBodyEntity body)) {
            return null;
        }
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (!MediumHudRenderer.isActiveMedium(viewer)
                || !MediumPlayerComponent.KEY.get(viewer).seesCorpse(body.getUuid())
                || NoellesHiddenBodiesBridge.isHidden(viewer.getWorld(), body.getPlayerUuid())) {
            return null;
        }
        return GetInstinctHighlight.HighlightResult.always(COLOR, PRIORITY);
    }
}
