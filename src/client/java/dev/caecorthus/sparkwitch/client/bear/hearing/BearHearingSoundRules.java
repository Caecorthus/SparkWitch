package dev.caecorthus.sparkwitch.client.bear.hearing;

import dev.caecorthus.sparkwitch.roles.civilian.bear.hearing.BearHearingRules;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

/**
 * Pure rules for which vanilla sounds the BEAR hearing factor scales (pending owner Q2, coordinator default): every
 * in-game sound the local player hears through their ears, own footsteps and shots included, so every
 * {@link SoundCategory} except {@link SoundCategory#MASTER} (menu and UI clicks, toasts) and {@link SoundCategory#MUSIC}
 * (the non-diegetic soundtrack). Jukebox records, weather, blocks, mobs, players, ambient and voice are in-world and
 * scaled. {@link BearHearingClient#EXEMPT_SOUND_IDS} (the wearer's own ricochet, visor hit and tinnitus) are never
 * scaled. Flipping Q2 is a one-line change to {@link #SCALED_CATEGORIES}. Gain only, never range: the seams sit after
 * vanilla derived the attenuation distance from the raw volume.
 * BEAR 听力倍数缩放哪些原版声音的纯规则（所有者 Q2 待定，协调者默认）：本地玩家用耳朵听到的所有游戏内声音，包括自己的
 * 脚步与枪声，即除 {@link SoundCategory#MASTER}（菜单与界面点击、提示）和 {@link SoundCategory#MUSIC}（非剧情内的配乐）外的
 * 所有类别。唱片机、天气、方块、生物、玩家、环境与语音都在世界中，因此都会缩放。{@link BearHearingClient#EXEMPT_SOUND_IDS}
 * （佩戴者自己的跳弹、面罩命中与耳鸣）从不缩放。改动 Q2 只需改 {@link #SCALED_CATEGORIES} 一行。只改增益不改范围：接缝位于
 * 原版用原始音量算出衰减距离之后。
 */
public final class BearHearingSoundRules {
    /** Q2 default: everything but menu/UI ({@code MASTER}) and the soundtrack ({@code MUSIC}). / Q2 默认值。 */
    public static final Set<SoundCategory> SCALED_CATEGORIES =
            EnumSet.complementOf(EnumSet.of(SoundCategory.MASTER, SoundCategory.MUSIC));

    /**
     * The largest factor any gear combination yields (earplugs alone, 1.5). Scaled sources get this as their OpenAL
     * {@code AL_MAX_GAIN} so a gain above 1 really amplifies; a gain at or below 1 is unaffected by the raised cap.
     * 任意装备组合可得的最大倍数（只戴耳塞，1.5）。受缩放的声源以此作为 OpenAL {@code AL_MAX_GAIN}，使大于 1 的增益真正放大；
     * 小于等于 1 的增益不受提高后的上限影响。
     */
    public static final float MAX_GAIN_CEILING = maxGearFactor();
    /** Minimum spacing of re-volume pushes. / 重设音量推送的最小间隔。 */
    public static final int REAPPLY_INTERVAL_TICKS = 2;
    /** Factor change below this is ignored. / 小于此值的倍数变化被忽略。 */
    static final float REAPPLY_EPSILON = 1.0E-3F;

    private BearHearingSoundRules() {
    }

    /** Whether the factor applies to a sound of this event id and category. / 倍数是否作用于该事件 id 与类别的声音。 */
    public static boolean scales(@Nullable Identifier soundId, @Nullable SoundCategory category) {
        return category != null && SCALED_CATEGORIES.contains(category)
                && (soundId == null || !BearHearingClient.EXEMPT_SOUND_IDS.contains(soundId));
    }

    /** {@code factor} for a scaled sound, 1 otherwise. / 受缩放的声音返回 {@code factor}，否则为 1。 */
    public static float volumeFactor(@Nullable Identifier soundId, @Nullable SoundCategory category, float factor) {
        return scales(soundId, category) ? factor : 1.0F;
    }

    /**
     * Whether to re-volume playing sources this tick: the factor moved since the last push, at most every
     * {@link #REAPPLY_INTERVAL_TICKS} (a gear change lands within that, an easing muffle steps at that rate, and the
     * settled value is always pushed because the difference persists until it is).
     * 本刻是否为正在播放的声源重设音量：倍数自上次推送后发生了变化，且至多每 {@link #REAPPLY_INTERVAL_TICKS} 刻一次（装备变化在
     * 此间隔内生效，缓动中的发闷按此频率步进，最终稳定值总会被推送，因为差异会一直存在直到推送）。
     */
    public static boolean shouldReapply(float lastApplied, float now, int ticksSinceReapply) {
        return Math.abs(now - lastApplied) > REAPPLY_EPSILON && ticksSinceReapply >= REAPPLY_INTERVAL_TICKS;
    }

    static float maxGearFactor() {
        float max = 1.0F;
        for (boolean helmet : new boolean[]{false, true}) {
            for (boolean earplugs : new boolean[]{false, true}) {
                max = Math.max(max, BearHearingRules.factor(helmet, earplugs));
            }
        }
        return max;
    }
}
