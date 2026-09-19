package dev.caecorthus.sparkwitch.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorSettings;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.factor.WitchFactorWorldComponent;
import dev.caecorthus.sparkwitch.util.SparkWitchPermissions;
import dev.doctor4t.wathe.Wathe;
import java.util.function.UnaryOperator;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/** Settings persist independently of the current round's immutable quota.
 * 设置独立持久化，不改变正在进行回合的额度快照。 */
public final class WitchFactorCommand {
    private WitchFactorCommand() { }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var root = CommandManager.literal("sparkwitch:witchFactor")
                .requires(Permissions.require("sparkwitch.command.witch_factor", SparkWitchPermissions.DEFAULT_COMMAND_LEVEL))
                .executes(context -> show(context.getSource()));
        var rule = CommandManager.literal("Rule");
        for (WitchFactorSettings.Rule mode : WitchFactorSettings.Rule.values()) {
            rule.then(CommandManager.literal(mode.name()).executes(context -> update(context.getSource(),
                    old -> new WitchFactorSettings(mode, old.divisor(), old.maxCarriers()))));
        }
        root.then(rule);
        root.then(CommandManager.literal("DivideMode")
                .then(CommandManager.argument("num", IntegerArgumentType.integer(1))
                        .executes(context -> update(context.getSource(), old -> new WitchFactorSettings(old.rule(),
                                IntegerArgumentType.getInteger(context, "num"), old.maxCarriers())))));
        root.then(CommandManager.literal("MaxCarrierMode")
                .then(CommandManager.literal("unlimited").executes(context -> update(context.getSource(),
                        old -> new WitchFactorSettings(old.rule(), old.divisor(), -1))))
                .then(CommandManager.argument("num", IntegerArgumentType.integer(0))
                        .executes(context -> update(context.getSource(), old -> new WitchFactorSettings(old.rule(),
                                old.divisor(), IntegerArgumentType.getInteger(context, "num"))))));
        dispatcher.register(root);
    }

    private static int update(ServerCommandSource source, UnaryOperator<WitchFactorSettings> edit) {
        return Wathe.executeSupporterCommand(source, () -> {
            WitchFactorWorldComponent component = WitchFactorWorldComponent.KEY.get(source.getWorld());
            component.setSettings(edit.apply(component.settings()));
            show(source);
        });
    }

    private static int show(ServerCommandSource source) {
        WitchFactorSettings settings = WitchFactorWorldComponent.KEY.get(source.getWorld()).settings();
        source.sendFeedback(() -> Text.translatable("command.sparkwitch.witch_factor.updated",
                settings.rule().name(), settings.divisor(), settings.maxCarriers() == -1 ? "unlimited" : settings.maxCarriers()), true);
        return 1;
    }
}
