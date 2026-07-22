package dev.caecorthus.sparkwitch.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import dev.doctor4t.wathe.Wathe;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/** Adds the SparkWitch-owned ghost dividend branch to Wathe's game settings tree. */
public final class WatheGhostDividendCommand {
    public static final String WATHE_PERMISSION = "wathe.command.gamesettings";

    private WatheGhostDividendCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("wathe:gameSettings")
                .requires(Permissions.require(WATHE_PERMISSION, 2))
                .then(CommandManager.literal("set")
                        .then(CommandManager.literal("roleDividend")
                                .then(CommandManager.literal("ghost")
                                        .then(CommandManager.argument("dividend", IntegerArgumentType.integer(1))
                                                .executes(context -> setDividend(
                                                        context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "dividend"))))))));
    }

    private static int setDividend(ServerCommandSource source, int dividend) {
        return Wathe.executeSupporterCommand(source, () -> {
            WitchWorldComponent.KEY.get(source.getWorld()).setWraithDividend(dividend);
            source.sendFeedback(() -> Text.translatable(
                    "command.sparkwitch.ghost_dividend.success", dividend), true);
        });
    }
}
