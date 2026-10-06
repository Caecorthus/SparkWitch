package dev.caecorthus.sparkwitch.client.grandwitch;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.witch.WitchFactionRules;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment.hold.RecruitmentHold;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment.hold.RecruitmentHoldRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Client reads of the Grand Witch recruitment hold. Keys, item use, screens and the hotbar scroll are already locked
 * through the Control Expert stun seam; this adds the camera lock and the held-item hiding. Only the server's zero sync
 * ends a hold, so the client never unlocks early.
 * 大魔女招募定身的客户端读取。按键、物品使用、界面与快捷栏滚轮已经通过控场专家眩晕接缝锁定；此处补充视角锁定
 * 与手持物隐藏。只有服务端同步的零值才会结束定身，因此客户端不会提前解锁。
 */
public final class RecruitmentHoldClient {
    private RecruitmentHoldClient() {
    }

    public static boolean isLocalHeld() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null
                && SparkWitchServerConnection.isConfirmedServer()
                && client.player != null
                && RecruitmentHold.isHeld(client.player);
    }

    /** Whether {@code holder}'s held items are hidden from the local player. / 是否对本地玩家隐藏 {@code holder} 的手持物。 */
    public static boolean hidesHeldItems(@Nullable PlayerEntity holder) {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity viewer = client == null ? null : client.player;
        return holder != null && viewer != null && SparkWitchServerConnection.isConfirmedServer()
                && RecruitmentHoldRules.hidesHeldItems(RecruitmentHold.isHeld(holder), viewer.isSpectator());
    }

    /**
     * Whether {@code candidate} is a held recruit that {@code viewer} must not perceive: the Wathe name-tag, aim and
     * crosshair raycasts pass through them (next to the Wraith case in {@code WraithNameTagPassThrough} and
     * {@code WraithAimPassThrough}) and outline hooks skip them. Reads only the synced hold.
     * {@code candidate} 是否为 {@code viewer} 不应察觉的被定身新共犯：Wathe 名牌、瞄准与准星射线穿过其身体（与
     * {@code WraithNameTagPassThrough}、{@code WraithAimPassThrough} 中的冤魂分支并列），描边钩子跳过其身体。
     * 只读取同步的定身状态。
     */
    public static boolean isHiddenFrom(@Nullable PlayerEntity viewer, @Nullable Entity candidate) {
        return viewer != null && candidate instanceof PlayerEntity target
                && SparkWitchServerConnection.isConfirmedServer()
                && RecruitmentHoldRules.hidesPresence(RecruitmentHold.isHeld(target), target == viewer,
                        viewer.isSpectator());
    }

    /**
     * Whether {@code viewer} gets no outline of the held recruit {@code candidate}: as {@link #isHiddenFrom}, except that
     * a witch-faction viewer's instinct still sees through (owner 2026-10-06). The viewer's own synced role decides.
     * {@code viewer} 是否看不到被定身新共犯 {@code candidate} 的描边：与 {@link #isHiddenFrom} 相同，但魔女阵营观察者的
     * 本能仍可透视（所有者 2026-10-06）。以观察者自身同步的身份判定。
     */
    public static boolean isOutlineHiddenFrom(@Nullable PlayerEntity viewer, @Nullable Entity candidate) {
        return viewer != null && candidate instanceof PlayerEntity target
                && SparkWitchServerConnection.isConfirmedServer()
                && RecruitmentHoldRules.hidesOutline(RecruitmentHold.isHeld(target), target == viewer,
                        viewer.isSpectator(),
                        WitchFactionRules.isWitchFactionMember(GameWorldComponent.KEY.get(viewer.getWorld()).getRole(viewer)));
    }

    /**
     * {@link #isOutlineHiddenFrom} for the local player, the viewer of every instinct outline.
     * 以本地玩家（所有本能描边的观察者）为观察者的 {@link #isOutlineHiddenFrom}。
     */
    public static boolean isOutlineHiddenFromLocalViewer(@Nullable Entity candidate) {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null && isOutlineHiddenFrom(client.player, candidate);
    }
}
