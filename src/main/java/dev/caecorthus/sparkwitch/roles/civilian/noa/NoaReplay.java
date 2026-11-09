package dev.caecorthus.sparkwitch.roles.civilian.noa;

import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/**
 * Replay for Noa: skill events under her role id. A painting stores the outcome name plus the item id or the sanity
 * amount (never a server-resolved string, so every viewer reads it in their own language); the dog's watch stores the
 * punished killer as the target.
 * 诺亚的回放：以其职业 id 记录的技能事件。作画保存结果名以及物品 id 或理智数值（从不保存服务端解析好的字符串，因此每位
 * 观看者都以自己的语言阅读）；大狗的监视把被惩罚的凶手保存为目标。
 */
final class NoaReplay {
    private static final String ACTION = "action";
    private static final String PAINT = "paint";
    private static final String DOG = "dog";
    private static final String OUTCOME = "outcome";
    private static final String ITEM = "item";
    private static final String AMOUNT = "amount";
    private static boolean registered;

    private NoaReplay() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerSkillFormatter(NoaRules.ROLE_ID, NoaReplay::format);
    }

    static void recordItem(ServerPlayerEntity noa, NoaPaintTable.Outcome outcome, Item item) {
        NbtCompound data = paint(outcome);
        data.putString(ITEM, Registries.ITEM.getId(item).toString());
        GameRecordManager.recordSkillUse(noa, NoaRules.ROLE_ID, null, data);
    }

    static void recordEffect(ServerPlayerEntity noa, NoaPaintTable.Outcome outcome) {
        GameRecordManager.recordSkillUse(noa, NoaRules.ROLE_ID, null, paint(outcome));
    }

    static void recordSanity(ServerPlayerEntity noa, NoaPaintTable.Outcome outcome, int deltaPercent) {
        NbtCompound data = paint(outcome);
        data.putInt(AMOUNT, deltaPercent);
        GameRecordManager.recordSkillUse(noa, NoaRules.ROLE_ID, null, data);
    }

    static void recordDogWatch(ServerPlayerEntity noa, ServerPlayerEntity killer) {
        NbtCompound data = new NbtCompound();
        data.putString(ACTION, DOG);
        GameRecordManager.recordSkillUse(noa, NoaRules.ROLE_ID, killer, data);
    }

    private static NbtCompound paint(NoaPaintTable.Outcome outcome) {
        NbtCompound data = new NbtCompound();
        data.putString(ACTION, PAINT);
        data.putString(OUTCOME, outcome.name().toLowerCase(Locale.ROOT));
        return data;
    }

    private static @Nullable Text format(GameRecordEvent event, GameRecordManager.MatchRecord match,
                                         ServerWorld world) {
        NbtCompound data = event.data();
        if (!data.containsUuid("actor")) {
            return null;
        }
        var players = ReplayGenerator.getPlayerInfoCache(match);
        Text actor = ReplayGenerator.formatPlayerName(data.getUuid("actor"), players);
        if (DOG.equals(data.getString(ACTION))) {
            if (!data.containsUuid("target")) {
                return null;
            }
            UUID target = data.getUuid("target");
            return Text.translatable("replay.skill.sparkwitch.noa.dog", actor,
                    ReplayGenerator.formatPlayerName(target, players));
        }
        if (!PAINT.equals(data.getString(ACTION))) {
            return null;
        }
        if (data.contains(ITEM)) {
            Identifier id = Identifier.tryParse(data.getString(ITEM));
            if (id == null || !Registries.ITEM.containsId(id)) {
                return null;
            }
            return Text.translatable("replay.skill.sparkwitch.noa.item", actor,
                    Text.translatable(Registries.ITEM.get(id).getTranslationKey()));
        }
        if (data.contains(AMOUNT)) {
            int amount = data.getInt(AMOUNT);
            return Text.translatable(amount > 0 ? "replay.skill.sparkwitch.noa.sanity_gain"
                    : "replay.skill.sparkwitch.noa.sanity_loss", actor, Math.abs(amount));
        }
        String outcome = data.getString(OUTCOME);
        return switch (outcome) {
            case "speed_ii", "speed_iii", "whiskey" ->
                    Text.translatable("replay.skill.sparkwitch.noa." + outcome, actor);
            default -> null;
        };
    }
}
