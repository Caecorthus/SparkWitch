package dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment;

import net.minecraft.util.Identifier;

import java.util.Objects;
import java.util.UUID;

/**
 * One admin-forced Grand Witch recruitment ({@code /sparkwitch:forceAccompliceRole}): the player that recruitment
 * takes and the role they get, the plain Accomplice or a special accomplice id. The recruitment number it belongs to
 * is the key it is stored under on the overworld {@code WitchWorldComponent}; the role id is resolved only when the
 * recruitment happens.
 * 一条管理员强制指定的大魔女招募（{@code /sparkwitch:forceAccompliceRole}）：该次招募选中的玩家及其获得的职业（普通共犯或
 * 特殊共犯 id）。所属的招募序号是它在主世界 {@code WitchWorldComponent} 上的存储键；职业 id 只在招募发生时解析。
 */
public record ForcedRecruit(UUID player, Identifier role) {
    public ForcedRecruit {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(role, "role");
    }
}
