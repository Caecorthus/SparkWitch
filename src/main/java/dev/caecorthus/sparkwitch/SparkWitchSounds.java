package dev.caecorthus.sparkwitch;

import dev.caecorthus.sparkwitch.roles.civilian.savior.SaviorRules;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecRules;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterRules;
import dev.caecorthus.sparkwitch.roles.killer.witchmaiden.wither.MiniWitherSoundSlot;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

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
    /**
     * Hunter double-barrel shotgun sounds (ids live in HunterRules): the shot carries 16 blocks and the reload 8.
     * 猎人双管猎枪音效（id 定义在 HunterRules）：枪声传播 16 格，装填 8 格。
     */
    public static final Identifier DOUBLE_BARREL_SHOT_ID = HunterRules.SHOT_SOUND_ID;
    public static final Identifier DOUBLE_BARREL_LOAD_ID = HunterRules.LOAD_SOUND_ID;
    /**
     * Savior pistol sounds (ids live in SaviorRules): the shot carries at Wathe's revolver volume 5.0 and the click at
     * 0.5. The bundled audio lives under {@code sparkwitch:sounds/item/} so it can be replaced.
     * 救世主手枪音效（id 定义在 SaviorRules）：枪声沿用 Wathe 左轮的 5.0 音量，点击声 0.5。音频位于
     * {@code sparkwitch:sounds/item/}，可直接替换。
     */
    public static final Identifier SAVIOR_PISTOL_SHOOT_ID = SaviorRules.PISTOL_SHOOT_SOUND_ID;
    public static final Identifier SAVIOR_PISTOL_CLICK_ID = SaviorRules.PISTOL_CLICK_SOUND_ID;
    /** Savior lightsaber impact (single CC0 variant); no subtitle. / 救世主光剑命中（单个 CC0 变体）；无字幕。 */
    public static final Identifier SAVIOR_LIGHTSABER_HIT_ID = SaviorRules.LIGHTSABER_HIT_SOUND_ID;
    /** Savior lightsaber ignition, requested by the client at the 0.95 s mark; no subtitle. / 救世主光剑点火音，客户端在 0.95 秒处请求；无字幕。 */
    public static final Identifier SAVIOR_LIGHTSABER_IGNITE_ID = SaviorRules.LIGHTSABER_IGNITE_SOUND_ID;
    /** Looping idle hum while the blade is lit; non-streamed so OpenAL loops it seamlessly. / 刀刃点亮时的循环嗡鸣；非流式以便 OpenAL 无缝循环。 */
    public static final Identifier SAVIOR_LIGHTSABER_HUM_ID = SaviorRules.LIGHTSABER_HUM_SOUND_ID;
    /** Lightsaber swing, requested on left-click only while the blade is lit. / 光剑挥动音，仅在刀刃点亮时左键请求。 */
    public static final Identifier SAVIOR_LIGHTSABER_SWING_ID = SaviorRules.LIGHTSABER_SWING_SOUND_ID;
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
    public static SoundEvent DOUBLE_BARREL_SHOT;
    public static SoundEvent DOUBLE_BARREL_LOAD;
    public static SoundEvent SAVIOR_PISTOL_SHOOT;
    public static SoundEvent SAVIOR_PISTOL_CLICK;
    public static SoundEvent SAVIOR_LIGHTSABER_HIT;
    public static SoundEvent SAVIOR_LIGHTSABER_IGNITE;
    public static SoundEvent SAVIOR_LIGHTSABER_HUM;
    public static SoundEvent SAVIOR_LIGHTSABER_SWING;
    /** Knight duel audio; no subtitles. / 骑士决斗音频；无字幕。 */
    public static final Identifier DUEL_MANBA_ID = dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelRules.MANBA_SOUND_ID;
    public static final Identifier DUEL_SF_ID = dev.caecorthus.sparkwitch.roles.civilian.knight.duel.KnightDuelRules.SF_SOUND_ID;
    public static SoundEvent DUEL_MANBA;
    public static SoundEvent DUEL_SF;
    /**
     * The Mini Wither's own events, one per {@link MiniWitherSoundSlot}: variable range like the vanilla events they
     * replaced, and sounds.json defaults each to the same vanilla files and subtitle. Read with {@link #miniWither}.
     * 小凋灵自有事件，每个 {@link MiniWitherSoundSlot} 一个：与被替换的原版事件一样为可变范围，sounds.json 默认指向相同的
     * 原版音频与字幕。通过 {@link #miniWither} 读取。
     */
    private static final Map<MiniWitherSoundSlot, SoundEvent> MINI_WITHER = new EnumMap<>(MiniWitherSoundSlot.class);
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
        DOUBLE_BARREL_SHOT = Registry.register(Registries.SOUND_EVENT, DOUBLE_BARREL_SHOT_ID,
                SoundEvent.of(DOUBLE_BARREL_SHOT_ID));
        DOUBLE_BARREL_LOAD = Registry.register(Registries.SOUND_EVENT, DOUBLE_BARREL_LOAD_ID,
                SoundEvent.of(DOUBLE_BARREL_LOAD_ID));
        SAVIOR_PISTOL_SHOOT = Registry.register(Registries.SOUND_EVENT, SAVIOR_PISTOL_SHOOT_ID,
                SoundEvent.of(SAVIOR_PISTOL_SHOOT_ID));
        SAVIOR_PISTOL_CLICK = Registry.register(Registries.SOUND_EVENT, SAVIOR_PISTOL_CLICK_ID,
                SoundEvent.of(SAVIOR_PISTOL_CLICK_ID));
        SAVIOR_LIGHTSABER_HIT = Registry.register(Registries.SOUND_EVENT, SAVIOR_LIGHTSABER_HIT_ID,
                SoundEvent.of(SAVIOR_LIGHTSABER_HIT_ID));
        SAVIOR_LIGHTSABER_IGNITE = Registry.register(Registries.SOUND_EVENT, SAVIOR_LIGHTSABER_IGNITE_ID,
                SoundEvent.of(SAVIOR_LIGHTSABER_IGNITE_ID));
        SAVIOR_LIGHTSABER_HUM = Registry.register(Registries.SOUND_EVENT, SAVIOR_LIGHTSABER_HUM_ID,
                SoundEvent.of(SAVIOR_LIGHTSABER_HUM_ID));
        SAVIOR_LIGHTSABER_SWING = Registry.register(Registries.SOUND_EVENT, SAVIOR_LIGHTSABER_SWING_ID,
                SoundEvent.of(SAVIOR_LIGHTSABER_SWING_ID));
        DUEL_MANBA = Registry.register(Registries.SOUND_EVENT, DUEL_MANBA_ID, SoundEvent.of(DUEL_MANBA_ID));
        DUEL_SF = Registry.register(Registries.SOUND_EVENT, DUEL_SF_ID, SoundEvent.of(DUEL_SF_ID));
        for (MiniWitherSoundSlot slot : MiniWitherSoundSlot.values()) {
            MINI_WITHER.put(slot, Registry.register(Registries.SOUND_EVENT, slot.eventId(),
                    SoundEvent.of(slot.eventId())));
        }
    }

    /** The registered event of one Mini Wither slot. / 某个小凋灵槽位已注册的事件。 */
    public static SoundEvent miniWither(MiniWitherSoundSlot slot) {
        return Objects.requireNonNull(MINI_WITHER.get(slot), "SparkWitchSounds.register() has not run");
    }
}
