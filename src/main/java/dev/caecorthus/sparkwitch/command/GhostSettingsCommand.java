package dev.caecorthus.sparkwitch.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import dev.caecorthus.sparkwitch.util.SparkWitchPermissions;
import dev.doctor4t.wathe.Wathe;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/** Registers persistent Wraith settings that take effect when the next round begins. */
public final class GhostSettingsCommand {
    private GhostSettingsCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("sparkwitch:ghostChance")
                .requires(Permissions.require(
                        SparkWitchPermissions.COMMAND_GHOST_CHANCE,
                        SparkWitchPermissions.DEFAULT_COMMAND_LEVEL))
                .then(CommandManager.argument("chance", IntegerArgumentType.integer(0, 100))
                        .executes(context -> setChance(
                                context.getSource(), IntegerArgumentType.getInteger(context, "chance")))));
        dispatcher.register(CommandManager.literal("sparkwitch:ghostMinRequirement")
                .requires(Permissions.require(
                        SparkWitchPermissions.COMMAND_GHOST_MIN_REQUIREMENT,
                        SparkWitchPermissions.DEFAULT_COMMAND_LEVEL))
                .then(CommandManager.argument("players", IntegerArgumentType.integer(0))
                        .executes(context -> setMinimum(
                                context.getSource(), IntegerArgumentType.getInteger(context, "players")))));
    }

    private static int setChance(ServerCommandSource source, int chance) {
        return Wathe.executeSupporterCommand(source, () -> {
            WitchWorldComponent.KEY.get(source.getWorld()).setWraithChance(chance);
            source.sendFeedback(() -> Text.translatable(
                    "command.sparkwitch.ghost_chance.success", chance), true);
        });
    }

    private static int setMinimum(ServerCommandSource source, int minimum) {
        return Wathe.executeSupporterCommand(source, () -> {
            WitchWorldComponent.KEY.get(source.getWorld()).setWraithMinimum(minimum);
            source.sendFeedback(() -> Text.translatable(
                    "command.sparkwitch.ghost_min_requirement.success", minimum), true);
        });
    }
}
