package dev.caecorthus.sparkwitch.command;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.util.SparkWitchPermissions;
import dev.doctor4t.wathe.Wathe;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

import java.util.Collection;

/**
 * Registers the /sparkwitch:setMana command.
 * 注册 /sparkwitch:setMana 命令，用于直接设定玩家魔力值。
 */
public final class SetManaCommand {
    private SetManaCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("sparkwitch:setMana")
                        .requires(Permissions.require(
                                SparkWitchPermissions.COMMAND_SET_MANA,
                                SparkWitchPermissions.DEFAULT_COMMAND_LEVEL
                        ))
                        .then(
                                CommandManager.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(context -> execute(
                                                context.getSource(),
                                                ImmutableList.of(context.getSource().getEntityOrThrow()),
                                                IntegerArgumentType.getInteger(context, "amount")
                                        ))
                                        .then(
                                                CommandManager.argument("targets", EntityArgumentType.entities())
                                                        .executes(context -> execute(
                                                                context.getSource(),
                                                                EntityArgumentType.getEntities(context, "targets"),
                                                                IntegerArgumentType.getInteger(context, "amount")
                                                        ))
                                        )
                        )
        );
    }

    private static int execute(ServerCommandSource source, Collection<? extends Entity> targets, int amount) {
        return Wathe.executeSupporterCommand(source, () -> {
            for (Entity target : targets) {
                WitchPlayerComponent.KEY.get(target).setMana(amount);
            }
        });
    }
}
