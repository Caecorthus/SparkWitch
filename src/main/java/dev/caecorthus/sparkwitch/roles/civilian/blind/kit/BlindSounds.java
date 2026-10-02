package dev.caecorthus.sparkwitch.roles.civilian.blind.kit;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Registers the Blind's sound events during mod initialization; the sounds.json entry reuses vanilla audio files (no
 * new asset). The cane tap uses vanilla's volume-scaled range, so it is as public as an ordinary player sound (D6).
 * 在模组初始化期间注册盲人的音效事件；sounds.json 条目复用原版音频文件（不新增资源）。盲杖敲击使用原版按音量缩放的
 * 传播范围，因此与普通玩家声音一样公开（D6）。
 */
public final class BlindSounds {
    public static final Identifier CANE_TAP_ID = SparkWitch.id("blind.cane_tap");
    private static SoundEvent caneTap;

    private BlindSounds() {
    }

    static synchronized void register() {
        if (caneTap != null) {
            return;
        }
        caneTap = Registry.register(Registries.SOUND_EVENT, CANE_TAP_ID, SoundEvent.of(CANE_TAP_ID));
    }

    public static SoundEvent caneTap() {
        if (caneTap == null) {
            throw new IllegalStateException("Blind sounds are not registered yet");
        }
        return caneTap;
    }
}
