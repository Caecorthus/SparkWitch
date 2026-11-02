package dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * The Mini Wither's own sound events ({@code sparkwitch:mini_wither.<key>}). Each one defaults, in sounds.json, to the
 * vanilla files it replaced, so nothing changes by default; resource packs and the client skin setting can then
 * replace the Mini Wither alone without touching the real Wither. The key is also the skin folder's sound file stem.
 * 小凋灵自有的声音事件（{@code sparkwitch:mini_wither.<key>}）。每个事件在 sounds.json 中默认指向它所替换的原版音频，
 * 因此默认效果不变；之后资源包与客户端皮肤设置可以只替换小凋灵，而不影响真正的凋灵。键名同时也是皮肤文件夹中声音文件的
 * 文件名主干。
 */
public enum MiniWitherSoundSlot {
    /** The quiet ambient call while summoned. / 召唤期间安静的环境叫声。 */
    IDLE("idle"),
    /** The 5 s auto-fire. / 每 5 秒的自动射击。 */
    SHOOT("shoot"),
    SUMMON("summon"),
    DISMISS("dismiss"),
    /** Owner-only: the second task unlocked the Mini Wither. / 仅主人可闻：第二个任务解锁了小凋灵。 */
    UNLOCK("unlock"),
    /** The Gohei's crimson skull launch. / 御币发射赤首。 */
    CRIMSON_SHOOT("crimson_shoot"),
    /** The crimson skull's impact. / 赤首命中。 */
    CRIMSON_HIT("crimson_hit"),
    /** A normal or blessed skull's 2x2x2 burst. / 普通或祝福头颅的 2x2x2 爆裂。 */
    BURST("burst");

    private final String key;
    private final Identifier eventId;

    MiniWitherSoundSlot(String key) {
        this.key = key;
        this.eventId = SparkWitch.id("mini_wither." + key);
    }

    public String key() {
        return key;
    }

    public Identifier eventId() {
        return eventId;
    }

    @Nullable
    public static MiniWitherSoundSlot byEventId(@Nullable Identifier id) {
        if (id == null) {
            return null;
        }
        for (MiniWitherSoundSlot slot : values()) {
            if (slot.eventId.equals(id)) {
                return slot;
            }
        }
        return null;
    }

    @Nullable
    public static MiniWitherSoundSlot byKey(String key) {
        for (MiniWitherSoundSlot slot : values()) {
            if (slot.key.equals(key)) {
                return slot;
            }
        }
        return null;
    }
}
