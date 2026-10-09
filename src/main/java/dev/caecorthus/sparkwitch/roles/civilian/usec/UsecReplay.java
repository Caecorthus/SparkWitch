package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Stable contract: the USEC rifle's Wathe replay lines (D19), one synthetic item-use record id
 * {@link #RECORD_ID} ({@code sparkwitch:usec_rifle_fire}; Wathe drops item uses without a registered formatter). A
 * fire record ({@link #KIND_FIRE}) carries the round ({@link #AMMO_KEY}), the blocks it pierced before stopping
 * ({@link #PENETRATED_KEY}) and Wathe's own {@code target} when it struck a player; a punishment record
 * ({@link #KIND_PUNISH}) names the innocent victim as {@code target}. Only match shots record; the death line still
 * comes from {@code killPlayer}, a Seeker break and a puppet end record their own lines. NBT keys are stable.
 * 稳定契约：USEC 步枪的 Wathe 回放行（D19），使用一个合成物品使用记录 id {@link #RECORD_ID}
 * （{@code sparkwitch:usec_rifle_fire}；Wathe 会丢弃没有注册格式化器的物品使用）。开火记录（{@link #KIND_FIRE}）携带弹种
 * （{@link #AMMO_KEY}）、停下前穿透的方块数（{@link #PENETRATED_KEY}），命中玩家时还有 Wathe 自身的 {@code target}；惩罚记录
 * （{@link #KIND_PUNISH}）以 {@code target} 记下无辜的受害者。只有对局射击才记录；死亡行仍由 {@code killPlayer} 记录，
 * 搜寻者设备损坏与皮套结束各自记录。NBT 键名稳定。
 */
public final class UsecReplay {
    /** Synthetic item-use record id. / 合成物品使用记录 id。 */
    public static final Identifier RECORD_ID = SparkWitch.id("usec_rifle_fire");
    static final String KIND_KEY = "kind";
    static final String KIND_FIRE = "fire";
    static final String KIND_PUNISH = "punish";
    static final String AMMO_KEY = "ammo";
    static final String PENETRATED_KEY = "penetrated";
    static final String PUNISHMENT_KEY = "punishment";
    /**
     * Achievement record contract W1 (2026-10-09), read by SparkAssist: path distance in blocks to the player the shot
     * hit (only on a player hit) and the shooter's server-side scope state when firing. No replay line reads them.
     * 成就记录契约 W1（2026-10-09），由 SparkAssist 读取：沿弹道到被命中玩家的路径距离（方块，仅命中玩家时写入），以及开火时
     * 射手的服务端开镜状态。回放行不读取它们。
     */
    static final String DISTANCE_KEY = "distance";
    static final String SCOPED_KEY = "scoped";
    /** Wathe's own keys for the recording player and its target. / Wathe 自身写入的记录玩家与目标键。 */
    static final String WATHE_ACTOR_KEY = "actor";
    static final String WATHE_TARGET_KEY = "target";

    static final String FIRE_MISS_KEY = "replay.item_use.sparkwitch.usec_rifle_fire.miss";
    static final String FIRE_HIT_KEY = "replay.item_use.sparkwitch.usec_rifle_fire.hit";
    static final String FIRE_MISS_PENETRATED_KEY = "replay.item_use.sparkwitch.usec_rifle_fire.miss_penetrated";
    static final String FIRE_HIT_PENETRATED_KEY = "replay.item_use.sparkwitch.usec_rifle_fire.hit_penetrated";
    static final String PUNISHED_KEY = "replay.item_use.sparkwitch.usec_rifle_fire.punished";
    static final String CONFISCATED_KEY = "replay.item_use.sparkwitch.usec_rifle_fire.confiscated";

    private static boolean registered;

    private UsecReplay() {
    }

    /** Registers the formatter once, from {@link UsecRifleFireService#register()}. / 只注册一次。 */
    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerItemUseFormatter(RECORD_ID, UsecReplay::format);
    }

    /** Extra data of a fire record. / 开火记录的附加数据。 */
    static NbtCompound fireData(UsecAmmoType ammo, int penetrated) {
        NbtCompound extra = new NbtCompound();
        extra.putString(KIND_KEY, KIND_FIRE);
        extra.putString(AMMO_KEY, ammo.id());
        extra.putInt(PENETRATED_KEY, Math.max(0, penetrated));
        return extra;
    }

    /**
     * Extra data of a match fire record: {@link #fireData(UsecAmmoType, int)} plus {@link #SCOPED_KEY}, and
     * {@link #DISTANCE_KEY} when {@code hitDistance} is not null (a player was hit).
     * 对局开火记录的附加数据：{@link #fireData(UsecAmmoType, int)} 加上 {@link #SCOPED_KEY}；{@code hitDistance} 非 null
     * （命中玩家）时再加 {@link #DISTANCE_KEY}。
     */
    static NbtCompound fireData(UsecAmmoType ammo, int penetrated, @Nullable Double hitDistance, boolean scoped) {
        NbtCompound extra = fireData(ammo, penetrated);
        if (hitDistance != null) {
            extra.putDouble(DISTANCE_KEY, Math.max(0.0, hitDistance));
        }
        extra.putBoolean(SCOPED_KEY, scoped);
        return extra;
    }

    /** Extra data of a punishment record. / 惩罚记录的附加数据。 */
    static NbtCompound punishData(UsecFireRules.Punishment punishment) {
        NbtCompound extra = new NbtCompound();
        extra.putString(KIND_KEY, KIND_PUNISH);
        extra.putString(PUNISHMENT_KEY, punishment.name());
        return extra;
    }

    /** Pure key choice for a fire line. / 开火行的纯文本键选择。 */
    static String fireKey(boolean hit, boolean penetrated) {
        if (hit) {
            return penetrated ? FIRE_HIT_PENETRATED_KEY : FIRE_HIT_KEY;
        }
        return penetrated ? FIRE_MISS_PENETRATED_KEY : FIRE_MISS_KEY;
    }

    /** Pure key choice for a punishment line; null for an unknown or NONE outcome. / 惩罚行的纯文本键选择。 */
    static @Nullable String punishKey(@Nullable String punishment) {
        if (UsecFireRules.Punishment.CONFISCATE.name().equals(punishment)) {
            return CONFISCATED_KEY;
        }
        if (UsecFireRules.Punishment.KILL_SHOOTER.name().equals(punishment)
                || UsecFireRules.Punishment.CUSTOM.name().equals(punishment)) {
            return PUNISHED_KEY;
        }
        return null;
    }

    private static @Nullable Text format(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                         @Nullable ServerWorld world) {
        NbtCompound data = event.data();
        if (!data.containsUuid(WATHE_ACTOR_KEY)) {
            return null;
        }
        var players = ReplayGenerator.getPlayerInfoCache(match);
        Text actor = ReplayGenerator.formatPlayerName(data.getUuid(WATHE_ACTOR_KEY), players);
        String kind = data.getString(KIND_KEY);
        if (KIND_PUNISH.equals(kind)) {
            String key = punishKey(data.getString(PUNISHMENT_KEY));
            if (key == null || !data.containsUuid(WATHE_TARGET_KEY)) {
                return null;
            }
            return Text.translatable(key, actor,
                    ReplayGenerator.formatPlayerName(data.getUuid(WATHE_TARGET_KEY), players));
        }
        if (!KIND_FIRE.equals(kind) || !data.contains(AMMO_KEY, NbtElement.STRING_TYPE)) {
            return null;
        }
        UsecAmmoType ammo = UsecAmmoType.fromId(data.getString(AMMO_KEY));
        if (ammo == null) {
            return null;
        }
        int penetrated = Math.max(0, data.getInt(PENETRATED_KEY));
        boolean hit = data.containsUuid(WATHE_TARGET_KEY);
        Text round = ammo.label();
        String key = fireKey(hit, penetrated > 0);
        if (hit) {
            Text target = ReplayGenerator.formatPlayerName(data.getUuid(WATHE_TARGET_KEY), players);
            return penetrated > 0
                    ? Text.translatable(key, actor, round, target, penetrated)
                    : Text.translatable(key, actor, round, target);
        }
        return penetrated > 0 ? Text.translatable(key, actor, round, penetrated) : Text.translatable(key, actor, round);
    }
}
