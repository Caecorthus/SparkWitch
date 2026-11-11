package dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

/** Persistent full-ledger format; never used for client synchronization.
 * 完整账本的持久化格式，绝不用于客户端同步。 */
final class WitchFactorCodec {
    private WitchFactorCodec() { }
    static void write(NbtCompound tag, WitchFactorSettings settings, WitchFactorState state) {
        tag.putInt("FactorSchema", 2);
        tag.putString("Rule", settings.rule().name());
        tag.putInt("Divisor", settings.divisor());
        tag.putInt("MaxCarriers", settings.maxCarriers());
        tag.putBoolean("Active", state.active());
        tag.putInt("OpeningParticipants", state.openingParticipants());
        tag.putInt("RoundLimit", state.limit());
        tag.putInt("Spent", state.spent());
        // Optional since 2026-11-11; absent in older saves, which never held a self-made factor.
        // 2026-11-11 起的可选字段；旧存档没有该字段，也从未有过自成因子。
        tag.putInt("SelfMade", state.selfMade());
        tag.putBoolean("SpeedUnlocked", state.speedUnlocked());
        NbtList factors = new NbtList();
        state.factors().forEach((holder, factor) -> {
            NbtCompound entry = new NbtCompound();
            entry.putUuid("Holder", holder); entry.putUuid("Owner", factor.owner());
            entry.putInt("ManaTicks", factor.manaTicks()); entry.putInt("MoodTicks", factor.moodTicks());
            entry.putInt("AdmissionTicks", factor.admissionTicks());
            // Optional since 2026-11-11, written only when set. / 2026-11-11 起的可选字段，仅在为真时写入。
            if (factor.selfMade()) entry.putBoolean("SelfMade", true);
            factors.add(entry);
        });
        tag.put("Factors", factors);
    }

    static WitchFactorSettings read(NbtCompound tag, WitchFactorState state) {
        state.clear();
        WitchFactorSettings settings = WitchFactorSettings.DEFAULT;
        if (tag.contains("Rule")) {
            try {
                settings = new WitchFactorSettings(WitchFactorSettings.Rule.valueOf(tag.getString("Rule")),
                        tag.getInt("Divisor"), tag.getInt("MaxCarriers"));
            } catch (IllegalArgumentException ignored) { }
        }
        if (!tag.getBoolean("Active")) return settings;
        int participants = Math.max(0, tag.getInt("OpeningParticipants"));
        int limit = tag.contains("RoundLimit") ? Math.max(-1, tag.getInt("RoundLimit")) : settings.limit(participants);
        state.begin(participants, limit);
        NbtList factors = tag.getList("Factors", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < factors.size(); i++) {
            NbtCompound entry = factors.getCompound(i);
            if (entry.containsUuid("Holder") && entry.containsUuid("Owner")) {
                java.util.UUID holder = entry.getUuid("Holder"), owner = entry.getUuid("Owner");
                // Migration: an entry saved before the per-factor marker is self-made exactly when it is its own owner
                // (a spread never targets its source); an heir's copy from such a save reads as an ordinary factor.
                // 迁移：在逐因子标记出现之前保存的条目，当且仅当来源即持有者时为自成因子（传播的目标永远不是来源本人）；这类存档中
                // 继承者的副本按普通因子读取。
                state.factors().putIfAbsent(holder, new WitchFactorState.Factor(owner,
                        entry.getInt("ManaTicks"), entry.getInt("MoodTicks"),
                        entry.contains("AdmissionTicks") ? entry.getInt("AdmissionTicks") : WitchFactorState.ADMISSION_TICKS,
                        entry.getBoolean("SelfMade") || owner.equals(holder)));
            }
        }
        state.restoreSelfMade(tag.getInt("SelfMade"));
        // Surviving records cannot establish historical usage; never refund old finite quotas.
        // 幸存记录不能证明历史使用次数，旧有限额度不得在迁移时返还。
        state.restoreProgress(tag.contains("Spent") ? tag.getInt("Spent") : Math.max(limit, factors.size()),
                tag.getBoolean("SpeedUnlocked"));
        return settings;
    }
}
