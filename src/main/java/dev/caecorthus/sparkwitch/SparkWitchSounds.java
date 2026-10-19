package dev.caecorthus.sparkwitch;

import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRules;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class SparkWitchSounds {
    public static final Identifier PIG_CHASE_ID = SparkWitch.id("skill.pig_chase");
    public static final Identifier GRAND_WITCH_CEREMONIAL_SWORD_BGM_ID =
            SparkWitch.id("ambient.grand_witch_ceremonial_sword_bgm");
    public static final Identifier SAINT_BELL_ID = SparkWitch.id("ambient.saint_bell");
    /** Reuses vanilla bell audio; no new asset. / 复用原版钟声音频，不新增资源。 */
    public static final Identifier BELL_RINGER_TOLL_ID = SparkWitch.id("ambient.bell_ringer_toll");
    /**
     * Victim-private Time Stealer Pocket Watch stage chime and final stop; both use bundled pocket-watch audio (never
     * bell audio) and keep ids distinct from the Bell Ringer so players and client volume groups can tell them apart.
     * 仅受害者可闻的窃时者怀表阶段鸣响与停摆；均使用自带的怀表音频（不用钟声），id 与敲钟人区分，便于玩家辨认与客户端音量分组。
     */
    public static final Identifier TIME_STEALER_CHIME_ID = SparkWitch.id("ambient.time_stealer_chime");
    public static final Identifier TIME_STEALER_FINAL_ID = SparkWitch.id("ambient.time_stealer_final");
    /**
     * Holy Flash tinnitus: a seamless 3 s loop played only on the flashed player's own client (relative, no
     * attenuation, non-streamed so OpenAL loops the buffer without a gap). The client excludes this id from the flash
     * muffle so the ring stays on top.
     * 圣光弹耳鸣：只在被闪玩家自己的客户端播放的 3 秒无缝循环（相对玩家、无衰减、非流式，OpenAL 直接循环缓冲区无间隙）。
     * 客户端在闪光压音中排除此 id，保证耳鸣始终最响。
     */
    public static final Identifier HOLY_FLASH_TINNITUS_ID = SparkWitch.id("skill.holy_flash_tinnitus");
    /**
     * USEC rifle sounds (ids live in UsecRules). Variable range: the shot carries by its volume (12 loud, 2.5
     * suppressed), and the server plays it publicly so the Blind can perceive it.
     * USEC 步枪音效（id 定义在 UsecRules）。可变范围：枪声按音量传播（未消音 12，消音 2.5），由服务端公开播放，盲人才能感知。
     */
    public static final Identifier USEC_RIFLE_SHOOT_ID = UsecRules.SHOOT_SOUND_ID;
    public static final Identifier USEC_RIFLE_SHOOT_SUPPRESSED_ID = UsecRules.SHOOT_SUPPRESSED_SOUND_ID;
    public static final Identifier USEC_RIFLE_BOLT_ID = UsecRules.BOLT_SOUND_ID;
    public static final Identifier USEC_RIFLE_MAGAZINE_ID = UsecRules.MAGAZINE_SOUND_ID;
    public static final Identifier USEC_RIFLE_LOAD_ROUND_ID = UsecRules.LOAD_ROUND_SOUND_ID;
    public static final Identifier USEC_RIFLE_SCOPE_ID = UsecRules.SCOPE_SOUND_ID;
    public static SoundEvent PIG_CHASE;
    public static SoundEvent GRAND_WITCH_CEREMONIAL_SWORD_BGM;
    public static SoundEvent SAINT_BELL;
    public static SoundEvent BELL_RINGER_TOLL;
    public static SoundEvent TIME_STEALER_CHIME;
    public static SoundEvent TIME_STEALER_FINAL;
    public static SoundEvent HOLY_FLASH_TINNITUS;
    public static SoundEvent USEC_RIFLE_SHOOT;
    public static SoundEvent USEC_RIFLE_SHOOT_SUPPRESSED;
    public static SoundEvent USEC_RIFLE_BOLT;
    public static SoundEvent USEC_RIFLE_MAGAZINE;
    public static SoundEvent USEC_RIFLE_LOAD_ROUND;
    public static SoundEvent USEC_RIFLE_SCOPE;
    private static boolean registered;

    private SparkWitchSounds() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PIG_CHASE = Registry.register(Registries.SOUND_EVENT, PIG_CHASE_ID, SoundEvent.of(PIG_CHASE_ID));
        GRAND_WITCH_CEREMONIAL_SWORD_BGM = Registry.register(
                Registries.SOUND_EVENT,
                GRAND_WITCH_CEREMONIAL_SWORD_BGM_ID,
                SoundEvent.of(GRAND_WITCH_CEREMONIAL_SWORD_BGM_ID)
        );
        SAINT_BELL = Registry.register(Registries.SOUND_EVENT, SAINT_BELL_ID, SoundEvent.of(SAINT_BELL_ID));
        BELL_RINGER_TOLL = Registry.register(
                Registries.SOUND_EVENT,
                BELL_RINGER_TOLL_ID,
                SoundEvent.of(BELL_RINGER_TOLL_ID)
        );
        TIME_STEALER_CHIME = Registry.register(
                Registries.SOUND_EVENT,
                TIME_STEALER_CHIME_ID,
                SoundEvent.of(TIME_STEALER_CHIME_ID)
        );
        TIME_STEALER_FINAL = Registry.register(
                Registries.SOUND_EVENT,
                TIME_STEALER_FINAL_ID,
                SoundEvent.of(TIME_STEALER_FINAL_ID)
        );
        HOLY_FLASH_TINNITUS = Registry.register(
                Registries.SOUND_EVENT,
                HOLY_FLASH_TINNITUS_ID,
                SoundEvent.of(HOLY_FLASH_TINNITUS_ID)
        );
        USEC_RIFLE_SHOOT = Registry.register(Registries.SOUND_EVENT, USEC_RIFLE_SHOOT_ID,
                SoundEvent.of(USEC_RIFLE_SHOOT_ID));
        USEC_RIFLE_SHOOT_SUPPRESSED = Registry.register(Registries.SOUND_EVENT, USEC_RIFLE_SHOOT_SUPPRESSED_ID,
                SoundEvent.of(USEC_RIFLE_SHOOT_SUPPRESSED_ID));
        USEC_RIFLE_BOLT = Registry.register(Registries.SOUND_EVENT, USEC_RIFLE_BOLT_ID,
                SoundEvent.of(USEC_RIFLE_BOLT_ID));
        USEC_RIFLE_MAGAZINE = Registry.register(Registries.SOUND_EVENT, USEC_RIFLE_MAGAZINE_ID,
                SoundEvent.of(USEC_RIFLE_MAGAZINE_ID));
        USEC_RIFLE_LOAD_ROUND = Registry.register(Registries.SOUND_EVENT, USEC_RIFLE_LOAD_ROUND_ID,
                SoundEvent.of(USEC_RIFLE_LOAD_ROUND_ID));
        // Fixed range: only players within SCOPE_SOUND_RANGE receive it. / 固定范围：仅 SCOPE_SOUND_RANGE 格内的玩家收到。
        USEC_RIFLE_SCOPE = Registry.register(Registries.SOUND_EVENT, USEC_RIFLE_SCOPE_ID,
                SoundEvent.of(USEC_RIFLE_SCOPE_ID, UsecRules.SCOPE_SOUND_RANGE));
    }
}
