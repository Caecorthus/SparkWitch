package dev.caecorthus.sparkwitch.roles.killer.blackraven;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Where Perception measures its 8-block radius from (plan §3.5): the player's own position, unless the installed
 * override answers a point. Raven Form installs the one override (the flown crow during a session). Server only.
 * 感知 8 格半径的测量中心（计划 §3.5）：默认为玩家自身位置，已安装的覆盖返回某点时改用该点。化鸦安装唯一的覆盖（会话期间取被操控的
 * 乌鸦）。仅服务端。
 */
public final class BlackRavenPerceptionCenter {
    private static volatile @Nullable Function<ServerPlayerEntity, @Nullable Vec3d> override;

    private BlackRavenPerceptionCenter() {
    }

    /** Installs (or with null removes) the single override. / 安装（传 null 则移除）唯一的覆盖。 */
    public static void setOverride(@Nullable Function<ServerPlayerEntity, @Nullable Vec3d> centre) {
        override = centre;
    }

    /** The override's point when it answers one, else the player's position. / 覆盖返回点时取该点，否则取玩家位置。 */
    public static Vec3d of(ServerPlayerEntity raven) {
        Function<ServerPlayerEntity, @Nullable Vec3d> current = override;
        Vec3d centre = current == null ? null : current.apply(raven);
        return centre != null ? centre : raven.getPos();
    }
}
