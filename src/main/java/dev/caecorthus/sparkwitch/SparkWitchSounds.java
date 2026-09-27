package dev.caecorthus.sparkwitch;

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
     * Victim-private Time Stealer stage chime and final toll; both reuse vanilla audio (no new asset) and keep ids
     * distinct from the Bell Ringer so client volume groups can tell them apart.
     * 仅受害者可闻的窃时者阶段钟声与终钟；均复用原版音频（不新增资源），id 与敲钟人区分，便于客户端音量分组。
     */
    public static final Identifier TIME_STEALER_CHIME_ID = SparkWitch.id("ambient.time_stealer_chime");
    public static final Identifier TIME_STEALER_FINAL_ID = SparkWitch.id("ambient.time_stealer_final");
    public static SoundEvent PIG_CHASE;
    public static SoundEvent GRAND_WITCH_CEREMONIAL_SWORD_BGM;
    public static SoundEvent SAINT_BELL;
    public static SoundEvent BELL_RINGER_TOLL;
    public static SoundEvent TIME_STEALER_CHIME;
    public static SoundEvent TIME_STEALER_FINAL;
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
    }
}
