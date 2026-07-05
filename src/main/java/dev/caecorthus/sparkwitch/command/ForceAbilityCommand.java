package dev.caecorthus.sparkwitch.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.api.WitchSkillDefinition;
import dev.caecorthus.sparkwitch.api.WitchSkillRegistry;
import dev.caecorthus.sparkwitch.component.WitchPlayerComponent;
import dev.caecorthus.sparkwitch.skill.WitchSkillLockValidationService;
import dev.caecorthus.sparkwitch.util.SparkWitchPermissions;
import dev.doctor4t.wathe.Wathe;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Registers the /sparkwitch:forceAbility command for next-round ability locks.
 * 注册 /sparkwitch:forceAbility 命令，用于锁定玩家下一局魔女能力。
 */
public final class ForceAbilityCommand {
    private static final DynamicCommandExceptionType UNKNOWN_SKILL =
            new DynamicCommandExceptionType(id -> Text.literal("Unknown witch ability: " + id));

    private ForceAbilityCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
                CommandManager.literal("sparkwitch:forceAbility")
                        .requires(Permissions.require(
                                SparkWitchPermissions.COMMAND_FORCE_ABILITY,
                                SparkWitchPermissions.DEFAULT_COMMAND_LEVEL
                        ))
                        .then(CommandManager.argument("ability", IdentifierArgumentType.identifier())
                                .suggests(ForceAbilityCommand::suggestSkills)
                                .then(CommandManager.argument("players", EntityArgumentType.players())
                                        .executes(context -> execute(
                                                context.getSource(),
                                                requireSkill(getSkillId(context)),
                                                EntityArgumentType.getPlayers(context, "players")
                                        ))))
        );
    }

    private static int execute(
            ServerCommandSource source,
            WitchSkillDefinition skill,
            Collection<ServerPlayerEntity> players
    ) {
        return Wathe.executeSupporterCommand(source, () -> {
            int changed = 0;
            String singleChangedPlayerName = null;
            for (ServerPlayerEntity player : players) {
                WitchSkillLockValidationService.RoleConflict conflict =
                        WitchSkillLockValidationService.findForcedRoleConflict(source, player, skill);
                if (conflict != null) {
                    source.sendFeedback(() -> Text.literal("Skipped " + player.getGameProfile().getName()
                            + ": " + WitchSkillLockValidationService.forceAbilityConflictMessage(conflict)), false);
                    continue;
                }

                if (WitchPlayerComponent.KEY.get(player).setForcedSkill(skill.id())) {
                    changed++;
                    singleChangedPlayerName = player.getGameProfile().getName();
                }
            }
            int finalChanged = changed;
            String finalSingleChangedPlayerName = finalChanged == 1 ? singleChangedPlayerName : null;
            source.sendFeedback(() -> formatForceAbilityFeedback(skill, finalChanged, finalSingleChangedPlayerName), true);
        });
    }

    static Text formatForceAbilityFeedback(WitchSkillDefinition skill, int changed, String singleChangedPlayerName) {
        MutableText message = Text.literal("Forced witch ability ")
                .append(Text.literal(formatSkillIdForCommand(skill.id())).withColor(skill.color()))
                .append(Text.literal(" for "));
        if (changed == 1 && singleChangedPlayerName != null) {
            return message.append(Text.literal(singleChangedPlayerName + "."));
        }
        return message.append(Text.literal(changed + " player(s)."));
    }

    private static Identifier getSkillId(CommandContext<ServerCommandSource> context) {
        return normalizeSkillId(IdentifierArgumentType.getIdentifier(context, "ability"));
    }

    static Identifier normalizeSkillId(Identifier parsed) {
        if (parsed.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
            return SparkWitch.id(parsed.getPath());
        }
        return parsed;
    }

    private static WitchSkillDefinition requireSkill(Identifier skillId) throws CommandSyntaxException {
        WitchSkillDefinition skill = WitchSkillRegistry.get(skillId);
        if (skill == null) {
            throw UNKNOWN_SKILL.create(skillId);
        }
        return skill;
    }

    static CompletableFuture<Suggestions> suggestSkills(
            CommandContext<ServerCommandSource> context,
            SuggestionsBuilder builder
    ) {
        for (WitchSkillDefinition skill : WitchSkillRegistry.values()) {
            String suggestion = formatSkillIdForCommand(skill.id());
            if (CommandSource.shouldSuggest(builder.getRemaining(), suggestion)) {
                builder.suggest(suggestion);
            }
        }
        return builder.buildFuture();
    }

    public static String formatSkillIdForCommand(Identifier skillId) {
        if (skillId.getNamespace().equals(SparkWitch.MOD_ID)) {
            return skillId.getPath();
        }
        return skillId.toString();
    }
}
