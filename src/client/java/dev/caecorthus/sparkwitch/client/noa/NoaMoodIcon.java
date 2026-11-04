package dev.caecorthus.sparkwitch.client.noa;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.noa.NoaRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.gui.MoodRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Noa's cyan mood icon, drawn by {@code mixin/noa/NoaMoodRendererMixin} in place of Wathe's civilian icon. The cyan
 * sprites live in the GUI atlas at {@code assets/sparkwitch/textures/gui/sprites/hud/noa_mood_*.png}: pixel copies of
 * Wathe's {@code wathe:hud/mood_happy/mid/depressive} (14x17, no {@code .mcmeta}, like Wathe's) with only the hue
 * changed. Display only: it reads Wathe's displayed {@code moodRender} sign and the local player's raw synced role,
 * never shows a sanity value and changes no state.
 * 诺亚的青色心情图标，由 {@code mixin/noa/NoaMoodRendererMixin} 替换 Wathe 的平民图标绘制。青色 sprite 位于 GUI 图集
 * {@code assets/sparkwitch/textures/gui/sprites/hud/noa_mood_*.png}：由 Wathe 的
 * {@code wathe:hud/mood_happy/mid/depressive}（14x17，与 Wathe 一样没有 {@code .mcmeta}）逐像素复制，只换色相。仅用于
 * 展示：只读取 Wathe 显示用 {@code moodRender} 的正负与本地玩家同步的真实职业，从不显示理智值，也不改变任何状态。
 */
public final class NoaMoodIcon {
    public static final Identifier CYAN_HAPPY = Identifier.of("sparkwitch", "hud/noa_mood_happy");
    public static final Identifier CYAN_MID = Identifier.of("sparkwitch", "hud/noa_mood_mid");
    public static final Identifier CYAN_DEPRESSIVE = Identifier.of("sparkwitch", "hud/noa_mood_depressive");

    private NoaMoodIcon() {
    }

    /**
     * The sprite Wathe's icon draw should use: the cyan copy for a local Noa on a confirmed SparkWitch server while the
     * displayed mood is at or above 0, else Wathe's own sprite. Cheapest checks first; every other client leaves before
     * any component lookup.
     * Wathe 图标绘制应使用的 sprite：已确认 SparkWitch 服务器上的本地诺亚在显示理智不低于 0 时用青色版，否则用 Wathe 自己的
     * sprite。先做开销最小的判断；其他客户端在任何组件查询之前返回。
     */
    public static Identifier spriteFor(Identifier watheSprite) {
        Identifier cyan = cyanCopy(watheSprite, MoodRenderer.moodRender);
        return cyan != null && isLocalNoa() ? cyan : watheSprite;
    }

    /**
     * Pure: the cyan copy of one of Wathe's three civilian icons while {@code displayedMood >= 0}, else {@code null}
     * (below 0 Wathe's original is kept; any other sprite passes through).
     * 纯函数：{@code displayedMood >= 0} 时返回 Wathe 三个平民图标之一的青色版，否则为 {@code null}（低于 0 时保留 Wathe
     * 原版；其他 sprite 原样放行）。
     */
    public static @Nullable Identifier cyanCopy(Identifier watheSprite, float displayedMood) {
        if (!(displayedMood >= 0.0F)) {
            return null;
        }
        if (MoodRenderer.MOOD_HAPPY.equals(watheSprite)) {
            return CYAN_HAPPY;
        }
        if (MoodRenderer.MOOD_MID.equals(watheSprite)) {
            return CYAN_MID;
        }
        if (MoodRenderer.MOOD_DEPRESSIVE.equals(watheSprite)) {
            return CYAN_DEPRESSIVE;
        }
        return null;
    }

    private static boolean isLocalNoa() {
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            return false;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return player != null && NoaRules.isNoa(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }
}
