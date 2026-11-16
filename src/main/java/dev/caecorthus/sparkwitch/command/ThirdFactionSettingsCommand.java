package dev.caecorthus.sparkwitch.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.util.SparkWitchPermissions;
import dev.doctor4t.wathe.Wathe;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/**
 * Registers the third-faction player minimum (owner 2026-11-16). The value lives in SparkFactionAPI, which owns the
 * round-start third-faction draw and reads it when the next round begins; 0 means no minimum. The member-cap dividend
 * stays on Wathe's {@code /wathe:gameSettings set roleDividend third_faction}, which SparkFactionAPI attaches.
 * 注册第三阵营最低人数命令（所有者 2026-11-16）。数值保存在负责开局第三阵营抽取的 SparkFactionAPI 中，下一局开始时读取；
 * 0 表示没有最低人数。成员上限除数仍由 SparkFactionAPI 挂在 Wathe 的
 * {@code /wathe:gameSettings set roleDividend third_faction} 上。
 */
public final class ThirdFactionSettingsCommand {
    private ThirdFactionSettingsCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("sparkwitch:thirdFactionMinRequirement")
                .requires(Permissions.require(
                        SparkWitchPermissions.COMMAND_THIRD_FACTION_MIN_REQUIREMENT,
                        SparkWitchPermissions.DEFAULT_COMMAND_LEVEL))
                .then(CommandManager.argument("players", IntegerArgumentType.integer(0))
                        .executes(context -> setMinimum(
                                context.getSource(), IntegerArgumentType.getInteger(context, "players")))));
    }

    /**
     * Fails closed like the lineup registration: a SparkFactionAPI 2.0.0 jar built before the third-faction draw lacks
     * the setter, so the command reports that and returns 0 instead of throwing.
     * 与阵容注册一样失败即关闭：在加入第三阵营抽取之前构建的 SparkFactionAPI 2.0.0 没有该设置方法，此时命令提示失败并返回
     * 0，而不是抛出异常。
     */
    private static int setMinimum(ServerCommandSource source, int players) {
        boolean[] applied = {false};
        int result = Wathe.executeSupporterCommand(source, () -> {
            try {
                SparkFactionApi.setThirdFactionMinPlayers(source.getServer(), players);
            } catch (LinkageError olderSparkFactionApi) {
                source.sendError(Text.translatable("command.sparkwitch.third_faction_min_requirement.unavailable"));
                return;
            }
            applied[0] = true;
            source.sendFeedback(() -> Text.translatable(
                    "command.sparkwitch.third_faction_min_requirement.success", players), true);
        });
        return applied[0] ? result : 0;
    }
}
