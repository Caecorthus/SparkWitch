package dev.caecorthus.sparkwitch.client.natsume;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.gui.MoodRenderer;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * 夏目安安's sketchbook mood icon (owner pick 2026-11-04, design A), drawn by
 * {@code mixin/natsume/NatsumeMoodRendererMixin} in place of Wathe's icon. She is a REAL-mood killer, so Wathe 1.5.6
 * draws her icon in {@code MoodRenderer.renderCivilian} (only FAKE-mood roles reach {@code renderKiller}). The sprites
 * live in the GUI atlas at {@code assets/sparkwitch/textures/gui/sprites/hud/natsume_mood_*.png}, 14x17 with no
 * {@code .mcmeta}, like Wathe's {@code wathe:hud/mood_happy/mid/depressive} and Noa's copies. Display only: reads the
 * local player's raw synced role and liveness, never a sanity value, and changes no state.
 * 夏目安安的素描本心情图标（所有者 2026-11-04 选定方案 A），由 {@code mixin/natsume/NatsumeMoodRendererMixin} 替换 Wathe 的
 * 图标绘制。她是真实理智的杀手，因此 Wathe 1.5.6 在 {@code MoodRenderer.renderCivilian} 中绘制其图标（只有伪装理智职业才会
 * 走 {@code renderKiller}）。sprite 位于 GUI 图集 {@code assets/sparkwitch/textures/gui/sprites/hud/natsume_mood_*.png}，
 * 14x17、无 {@code .mcmeta}，与 Wathe 的 {@code wathe:hud/mood_happy/mid/depressive} 以及诺亚的副本相同。仅用于展示：只读取
 * 本地玩家同步的真实职业与存活状态，从不读取理智值，也不改变任何状态。
 */
public final class NatsumeMoodIcon {
    public static final Identifier SKETCH_HAPPY = Identifier.of("sparkwitch", "hud/natsume_mood_happy");
    public static final Identifier SKETCH_MID = Identifier.of("sparkwitch", "hud/natsume_mood_mid");
    public static final Identifier SKETCH_DEPRESSIVE = Identifier.of("sparkwitch", "hud/natsume_mood_depressive");

    private NatsumeMoodIcon() {
    }

    /**
     * The sprite Wathe's icon draw should use: the sketchbook copy for a living, playing local 夏目安安 on a confirmed
     * SparkWitch server, else Wathe's own sprite. The pure sprite match runs first, so other sprites leave before any
     * component lookup.
     * Wathe 图标绘制应使用的 sprite：已确认 SparkWitch 服务器上存活且在局的本地夏目安安使用素描本版，否则使用 Wathe 自己的
     * sprite。先做纯 sprite 匹配，其他 sprite 在任何组件查询之前返回。
     */
    public static Identifier spriteFor(Identifier watheSprite) {
        Identifier sketch = sketchCopy(watheSprite);
        return sketch != null && isLocalNatsume() ? sketch : watheSprite;
    }

    /**
     * Pure: the sketchbook copy of one of Wathe's three civilian icons, else {@code null} (any other sprite, including
     * Noa's cyan copies, passes through).
     * 纯函数：Wathe 三个平民图标之一的素描本版，否则为 {@code null}（其他 sprite，包括诺亚的青色版，原样放行）。
     */
    public static @Nullable Identifier sketchCopy(Identifier watheSprite) {
        if (MoodRenderer.MOOD_HAPPY.equals(watheSprite)) {
            return SKETCH_HAPPY;
        }
        if (MoodRenderer.MOOD_MID.equals(watheSprite)) {
            return SKETCH_MID;
        }
        if (MoodRenderer.MOOD_DEPRESSIVE.equals(watheSprite)) {
            return SKETCH_DEPRESSIVE;
        }
        return null;
    }

    private static boolean isLocalNatsume() {
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            return false;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return player != null
                && NatsumeClientState.isNatsume(GameWorldComponent.KEY.get(player.getWorld()).getRole(player))
                && GameFunctions.isPlayerPlayingAndAlive(player);
    }
}
