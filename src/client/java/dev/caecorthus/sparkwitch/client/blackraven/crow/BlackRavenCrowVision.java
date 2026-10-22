package dev.caecorthus.sparkwitch.client.blackraven.crow;

import dev.caecorthus.sparkwitch.client.blackraven.BlackRavenClientState;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Client-only vision through the crow (plan §2.9, §2.10). Night vision is a lightmap override while the camera is the
 * crow the local player flies, never a status effect (Wathe clears everyone's night vision when a blackout ends), and
 * the darkness effect is ignored there. Vanilla fog and sky read the CAMERA entity's effects: the crow has none, so the
 * body's blackout blindness never shows through it, while Perception's blindness is forwarded on purpose
 * ({@link #fogSubject}) so Perception looks the same as on foot (its desaturation and Steve skins already key on the
 * local player).
 * 透过乌鸦的纯客户端视野（计划 §2.9、§2.10）。夜视是镜头为本地玩家所操控乌鸦时的光照贴图覆盖，从不使用状态效果（Wathe 在熄灯结束时
 * 会清除所有人的夜视），此时也忽略黑暗效果。原版雾与天空读取镜头实体的效果：乌鸦没有任何效果，因此本体的熄灯致盲不会透过乌鸦显示；
 * 感知的致盲则被有意转发（{@link #fogSubject}），使感知与步行时一致（其去色与 Steve 皮肤本就以本地玩家为准）。
 */
public final class BlackRavenCrowVision {
    private BlackRavenCrowVision() {
    }

    /** The camera is the crow the local player flies. / 镜头正是本地玩家操控的乌鸦。 */
    public static boolean viewingCrow() {
        if (!BlackRavenCrowPilotClient.isActive()) {
            return false;
        }
        Entity camera = MinecraftClient.getInstance().getCameraEntity();
        return camera instanceof BlackRavenCrowEntity crow && crow == BlackRavenCrowPilotClient.boundCrow();
    }

    /** Lightmap: full night vision and no darkness through the crow. / 光照贴图：透过乌鸦时满夜视且无黑暗。 */
    public static boolean grantsNightVision() {
        return viewingCrow();
    }

    /**
     * The entity whose effects drive fog and sky: the local body while Perception is open and the camera is the crow,
     * otherwise the camera entity itself. / 驱动雾与天空效果的实体：感知开启且镜头为乌鸦时取本地本体，否则为镜头实体本身。
     */
    public static @Nullable Entity fogSubject(@Nullable Entity focused) {
        if (!(focused instanceof BlackRavenCrowEntity) || !viewingCrow()) {
            return focused;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return forwardsPerception(player != null && BlackRavenClientState.isPerceptionActive(player))
                ? player : focused;
    }

    /** Pure: only Perception's window is forwarded to the crow camera. / 纯判定：只有感知窗口被转发给乌鸦镜头。 */
    static boolean forwardsPerception(boolean perceptionActive) {
        return perceptionActive;
    }
}
