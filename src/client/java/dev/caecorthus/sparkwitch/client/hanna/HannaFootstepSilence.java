package dev.caecorthus.sparkwitch.client.hanna;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.hanna.HannaComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Client half of the float's silence. The server already withholds a floating Hanna's sounds from everyone else, but
 * the client mod Presence Footsteps synthesises OTHER players' movement sounds locally and never asks the server, so
 * {@code hanna.HannaPresenceFootstepsVolumeMixin} zeroes that volume here. Reads only the float ticks synced to every
 * client; she still hears herself, exactly as on the server path. Fail closed: an unconfirmed server, a missing
 * listener or a missing component never mutes anything.
 * 浮游静音的客户端部分。服务端已不向其他人发送浮游中汉娜的声音，但客户端模组 Presence Footsteps 会在本地合成其他玩家的移动
 * 音效、从不询问服务端，因此由 {@code hanna.HannaPresenceFootstepsVolumeMixin} 在此把该音量归零。只读取同步给每个客户端的
 * 浮游剩余刻；她自己仍能听到自己，与服务端路径一致。失败即关闭：服务端未确认、无收听者或组件缺失时从不静音。
 */
public final class HannaFootstepSilence {
    private HannaFootstepSilence() {
    }

    /**
     * Whether {@code listener}'s client must not synthesise movement sounds for {@code source}.
     * 收听者的客户端是否不得为该来源合成移动音效。
     */
    public static boolean mutes(@Nullable PlayerEntity listener, @Nullable Entity source) {
        if (listener == null || !(source instanceof PlayerEntity sourcePlayer) || sourcePlayer == listener) {
            return false;
        }
        HannaComponent state = HannaComponent.KEY.getNullable(sourcePlayer);
        return mutes(SparkWitchServerConnection.isConfirmedServer(), false, state != null && state.isFloating());
    }

    /** Pure decision. / 纯判定。 */
    public static boolean mutes(boolean confirmedServer, boolean self, boolean sourceFloating) {
        return confirmedServer && !self && sourceFloating;
    }
}
