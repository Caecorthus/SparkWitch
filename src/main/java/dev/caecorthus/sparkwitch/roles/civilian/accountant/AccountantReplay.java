package dev.caecorthus.sparkwitch.roles.civilian.accountant;

import dev.doctor4t.wathe.record.GameRecordEvent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Replay of passbook transfers: one skill event per successful deposit ({@code sparkwitch:accountant_deposit}) or
 * withdrawal ({@code sparkwitch:accountant_withdraw}) carrying the amount; refused or empty requests record nothing.
 * Shown as "%s stored %s coins in a passbook" / "%s took %s coins out of a passbook".
 * 存折转账的回放：每次成功存入（{@code sparkwitch:accountant_deposit}）或取出（{@code sparkwitch:accountant_withdraw}）
 * 记录一个带金额的技能事件；被拒绝或空存折的请求什么也不记录。显示为“%s 向存折中存入了 %s 金币”/“%s 从存折中取出了 %s 金币”。
 */
final class AccountantReplay {
    private static boolean registered;

    private AccountantReplay() {
    }

    static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ReplayRegistry.registerSkillFormatter(AccountantRules.DEPOSIT_SKILL_ID,
                (event, match, world) -> format("replay.skill.sparkwitch.accountant_deposit", event, match));
        ReplayRegistry.registerSkillFormatter(AccountantRules.WITHDRAW_SKILL_ID,
                (event, match, world) -> format("replay.skill.sparkwitch.accountant_withdraw", event, match));
    }

    static void recordDeposit(ServerPlayerEntity player, int amount) {
        record(player, AccountantRules.DEPOSIT_SKILL_ID, amount);
    }

    static void recordWithdraw(ServerPlayerEntity player, int amount) {
        record(player, AccountantRules.WITHDRAW_SKILL_ID, amount);
    }

    private static void record(ServerPlayerEntity player, Identifier skillId, int amount) {
        NbtCompound extra = new NbtCompound();
        extra.putInt(AccountantRules.REPLAY_AMOUNT_KEY, amount);
        GameRecordManager.recordSkillUse(player, skillId, null, extra);
    }

    private static @Nullable Text format(String key, GameRecordEvent event, GameRecordManager.MatchRecord match) {
        if (!event.data().containsUuid("actor")) {
            return null;
        }
        return Text.translatable(key,
                ReplayGenerator.formatPlayerName(event.data().getUuid("actor"),
                        ReplayGenerator.getPlayerInfoCache(match)),
                event.data().getInt(AccountantRules.REPLAY_AMOUNT_KEY));
    }
}
