package dev.caecorthus.sparkwitch.roles.witch.potiongunner;

import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Role-owned replay formatter for launcher shots (Wathe silently drops unformatted item uses). One record per shot
 * under {@link PotionGunnerRules#FIRE_REPLAY_ID}, carrying the stable shell key under {@link #SHELL_KEY}; the line is
 * (actor, shell name). Malformed records return {@code null}, which Wathe skips.
 * 药炮手自有的炮筒发射回放格式化器（Wathe 会静默丢弃未注册格式化器的物品使用记录）。每次发射在
 * {@link PotionGunnerRules#FIRE_REPLAY_ID} 下记录一条，携带 {@link #SHELL_KEY} 下的稳定炮弹 key；该行为（使用者、炮弹名）。
 * 数据不完整的记录返回 {@code null}，Wathe 会跳过该行。
 */
public final class PotionGunnerReplay {
    static final String FIRE_KEY = "replay.item_use.sparkwitch.potion_launcher_fire";
    /** Stable record key for the fired shell; do not rename. / 所发射炮弹的稳定记录键，不得改名。 */
    static final String SHELL_KEY = "shell";
    /** Wathe's own key for the recording player ({@code GameRecordManager#addEvent}). / Wathe 自身写入的记录玩家键。 */
    static final String WATHE_ACTOR_KEY = "actor";

    private static boolean registered;

    private PotionGunnerReplay() {
    }

    /** Registers the formatter once; called only from {@link PotionGunnerLifecycle}. / 只注册一次；仅由生命周期调用。 */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerItemUseFormatter(PotionGunnerRules.FIRE_REPLAY_ID, PotionGunnerReplay::formatFire);
    }

    /** The extra data recorded for one shot. / 一次发射所记录的附加数据。 */
    public static NbtCompound fireData(PotionShellType type) {
        NbtCompound extra = new NbtCompound();
        extra.putString(SHELL_KEY, type.key());
        return extra;
    }

    /** Lang key of a shell item's name. / 炮弹物品名称的语言键。 */
    static String shellNameKey(PotionShellType type) {
        return type.itemId().toTranslationKey("item");
    }

    /** The shell type of a recorded shot, or empty when the record is malformed. / 记录中的炮弹种类；数据不完整时为空。 */
    static Optional<PotionShellType> recordedShell(NbtCompound data) {
        if (!data.containsUuid(WATHE_ACTOR_KEY) || !data.contains(SHELL_KEY, NbtElement.STRING_TYPE)) {
            return Optional.empty();
        }
        return PotionShellType.byKey(data.getString(SHELL_KEY));
    }

    /** (actor, shell name in its colour). / （使用者、带颜色的炮弹名）。 */
    static @Nullable Text formatFire(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                     @Nullable ServerWorld world) {
        NbtCompound data = event.data();
        Optional<PotionShellType> shell = recordedShell(data);
        if (shell.isEmpty()) {
            return null;
        }
        Text actor = ReplayGenerator.formatPlayerName(data.getUuid(WATHE_ACTOR_KEY),
                ReplayGenerator.getPlayerInfoCache(match));
        return Text.translatable(FIRE_KEY, actor,
                Text.translatable(shellNameKey(shell.get())).withColor(shell.get().color()));
    }
}
