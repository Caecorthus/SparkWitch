package dev.caecorthus.sparkwitch.roles.witch.riftwalker;

import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Match binding shared by every Riftwalker package (gate entities, the gate registry, sessions and cooldowns): Wathe's
 * replay match UUID as a string, the same format as the Seeker's binding, copied here so Riftwalker code never depends
 * on the Seeker module. Server only; null on a client or when no match is active, and a null binding never matches.
 * 隙行者各包共用的对局绑定（门实体、门登记表、会话与冷却）：Wathe 回放对局 UUID 的字符串形式，与搜寻者的绑定格式相同；
 * 复制到这里，使隙行者代码不依赖搜寻者模块。仅服务端；在客户端或无进行中对局时为 null，null 绑定永不匹配。
 */
public final class RiftwalkerMatch {
    private RiftwalkerMatch() {
    }

    @Nullable
    public static String currentMatchId(World world) {
        if (world == null || world.isClient() || !GameRecordManager.hasActiveMatch()) {
            return null;
        }
        GameRecordManager.MatchRecord match = GameRecordManager.getCurrentMatch();
        return match == null || match.getMatchId() == null ? null : match.getMatchId().toString();
    }

    /** Pure: a bound id matches only a non-null equal current id. / 纯规则：仅当当前 id 非空且相等时匹配。 */
    public static boolean matches(@Nullable String bound, @Nullable String current) {
        return bound != null && Objects.equals(bound, current);
    }
}
