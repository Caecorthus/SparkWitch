package dev.caecorthus.sparkwitch.client.grandwitch;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment.hold.RecruitmentHold;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment.hold.RecruitmentHoldRules;
import net.minecraft.client.MinecraftClient;
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
}
