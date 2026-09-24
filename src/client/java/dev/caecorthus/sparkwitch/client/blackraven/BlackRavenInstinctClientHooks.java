package dev.caecorthus.sparkwitch.client.blackraven;

import dev.caecorthus.sparkwitch.client.vendetta.VendettaClientPresentation;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenIdentitySnapshot;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenMarkPlayerComponent;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenRules;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/** Resolves Feather marks and the terminal sensed-only player outline policy. / 裁决羽刃标记与感知模式的最终玩家外框规则。 */
public final class BlackRavenInstinctClientHooks {
    private static final int FEATHER_PRIORITY = GetInstinctHighlight.HighlightResult.PRIORITY_HIGH + 1;
    private static boolean registered;

    private BlackRavenInstinctClientHooks() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        GetInstinctHighlight.EVENT.register(BlackRavenInstinctClientHooks::featherHighlight);
    }

    public static int resolveHighlight(int originalColor, Entity target) {
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer != null && target instanceof PlayerEntity targetPlayer) {
            Integer vendettaHighlight = VendettaClientPresentation.highlight(viewer, targetPlayer);
            if (vendettaHighlight != null) {
                return vendettaHighlight;
            }
        }
        if (viewer == null || !BlackRavenClientState.isEligible(viewer)) {
            return originalColor;
        }

        boolean marked = isMarkedForLocalRaven(target);
        if (BlackRavenClientState.isPerceptionActive(viewer)) {
            return marked ? BlackRavenRules.COLOR : -1;
        }
        if (BlackRavenClientState.mode() == BlackRavenClientState.InstinctMode.NORMAL) {
            return marked ? BlackRavenRules.COLOR : originalColor;
        }
        /*
         * 感知模式显示的是黑羽鸦已经完成积点后冻结下来的身份快照，
         * 优先级应高于 SparkTraits 善良词条自己的普通本能范围裁决。
         *
         * 这里不能再用 Wathe 原始返回色是否为隐藏结果作为前置门槛：善良词条会在 Wathe
         * getInstinctHighlight 的 HEAD 阶段先返回 -1，表示“善良本能此刻不显示该目标”。
         * 如果继续信任这个 -1，黑羽鸦已解锁的感知身份色就会被善良词条误挡掉。
         *
         * 真正必须尊重的硬隐藏仍保留在 isPubliclyVisible 里，例如目标死亡/旁观、
         * 隐身，以及 SparkTraits 对背水一战、灵魂投射、关灯隐藏等状态的屏蔽。
         */
        if (!(target instanceof PlayerEntity targetPlayer)
                || !WatheClient.isInstinctEnabled()
                || !isPubliclyVisible(viewer, targetPlayer)) {
            return -1;
        }

        BlackRavenIdentitySnapshot snapshot = BlackRavenClientState.snapshot(viewer, targetPlayer);
        return snapshot == null ? -1 : snapshot.roleColor();
    }

    public static @Nullable Integer resolvePrioritySensedHighlight(Entity target) {
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null
                || !BlackRavenClientState.isEligible(viewer)
                || BlackRavenClientState.mode() != BlackRavenClientState.InstinctMode.SENSED_ONLY) {
            return null;
        }

        /*
         * 这个入口在 WatheClient.getInstinctHighlight 的 HEAD 阶段运行，早于 SparkTraits
         * 的善良词条 HEAD 逻辑。善良会把“黑羽鸦本体”改造成有效好人，并直接返回
         * 善良本能颜色或 -1；如果不在这里提前接管，后续 RETURN 裁决可能拿不到机会。
         *
         * 感知模式仍然尊重其它模组显式给出的 skip，例如背水一战、灵魂投射、
         * 生存大师遮挡和魔女恐惧/障眼等硬隐藏；只有非 skip 的普通高亮才会被黑羽鸦
         * 已完成感知快照覆盖。
         */
        GetInstinctHighlight.HighlightResult eventResult = GetInstinctHighlight.EVENT.invoker().getHighlight(target);
        if (eventResult != null && eventResult.isSkip()) {
            return -1;
        }

        if (!(target instanceof PlayerEntity targetPlayer)
                || !WatheClient.isInstinctEnabled()
                || !isPubliclyVisible(viewer, targetPlayer)) {
            return -1;
        }

        BlackRavenIdentitySnapshot snapshot = BlackRavenClientState.snapshot(viewer, targetPlayer);
        return snapshot == null ? -1 : snapshot.roleColor();
    }

    public static boolean isPubliclyVisible(PlayerEntity viewer, PlayerEntity target) {
        return target != viewer
                && GameFunctions.isPlayerPlayingAndAlive(target)
                && !GameFunctions.isPlayerSpectatingOrCreative(target)
                && !target.isInvisibleTo(viewer)
                && !SparkTraitsInstinctVisibilityBridge.isHidden(viewer, target);
    }

    private static @Nullable GetInstinctHighlight.HighlightResult featherHighlight(Entity target) {
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null
                || !BlackRavenClientState.isEligible(viewer)
                || BlackRavenClientState.mode() == BlackRavenClientState.InstinctMode.SENSED_ONLY
                || !isMarkedForLocalRaven(target)) {
            return null;
        }
        return GetInstinctHighlight.HighlightResult.always(BlackRavenRules.COLOR, FEATHER_PRIORITY);
    }

    private static boolean isMarkedForLocalRaven(Entity target) {
        return target instanceof PlayerEntity targetPlayer
                && BlackRavenMarkPlayerComponent.KEY.get(targetPlayer).isMarkedForLocalRaven();
    }
}
