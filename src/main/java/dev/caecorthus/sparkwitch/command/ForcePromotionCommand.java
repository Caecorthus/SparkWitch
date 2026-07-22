package dev.caecorthus.sparkwitch.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import dev.caecorthus.sparkwitch.util.RoleDisplayTextRules;
import dev.caecorthus.sparkwitch.util.SparkWitchPermissions;
import dev.doctor4t.wathe.Wathe;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Registers /sparkwitch:forcePromotion for future Wraith promotion locks. */
public final class ForcePromotionCommand {
    private static final DynamicCommandExceptionType UNKNOWN_ROLE = new DynamicCommandExceptionType(
            id -> Text.translatable("command.sparkwitch.force_promotion.unknown_role", id)
    );
    private static final SimpleCommandExceptionType ROUND_RUNNING = new SimpleCommandExceptionType(
            Text.translatable("command.sparkwitch.force_promotion.round_running")
    );
    private static final List<PromotionRole> PROMOTION_ROLES = List.of(
            new PromotionRole(SparkWitchRoles.WIND_SPIRIT_ID, SparkWitchRoles::windSpirit),
            new PromotionRole(SparkWitchRoles.GUARDIAN_ANGEL_ID, SparkWitchRoles::guardianAngel),
            new PromotionRole(SparkWitchRoles.VENDETTA_ID, SparkWitchRoles::vendetta),
            new PromotionRole(SparkWitchRoles.SABOTEUR_ID, SparkWitchRoles::saboteur),
            new PromotionRole(SparkWitchRoles.CURSER_ID, SparkWitchRoles::curser)
    );

    private ForcePromotionCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("sparkwitch:forcePromotion")
                .requires(Permissions.require(
                        SparkWitchPermissions.COMMAND_FORCE_PROMOTION,
                        SparkWitchPermissions.DEFAULT_COMMAND_LEVEL
                ))
                .then(CommandManager.argument("promotionRole", IdentifierArgumentType.identifier())
                        .suggests(ForcePromotionCommand::suggestPromotionRoles)
                        .then(CommandManager.argument("players", EntityArgumentType.players())
                                .executes(context -> execute(
                                        context.getSource(),
                                        requireRole(normalizeRoleId(IdentifierArgumentType.getIdentifier(
                                                context, "promotionRole"))),
                                        EntityArgumentType.getPlayers(context, "players")
                                )))));
    }

    private static int execute(
            ServerCommandSource source,
            Role role,
            Collection<ServerPlayerEntity> players
    ) throws CommandSyntaxException {
        var overworld = source.getServer().getOverworld();
        if (GameWorldComponent.KEY.get(overworld).isRunning()) {
            throw ROUND_RUNNING.create();
        }
        return Wathe.executeSupporterCommand(source, () -> {
            WitchWorldComponent world = WitchWorldComponent.KEY.get(overworld);
            int changed = 0;
            for (ServerPlayerEntity player : players) {
                if (world.setForcedWraithPromotion(player.getUuid(), role.identifier())) {
                    changed++;
                }
            }
            int finalChanged = changed;
            source.sendFeedback(() -> Text.translatable(
                    "command.sparkwitch.force_promotion.success",
                    finalChanged,
                    Text.translatable(RoleDisplayTextRules.roleTranslationKey(role))
            ), true);
        });
    }

    static Identifier normalizeRoleId(Identifier parsed) {
        return parsed.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)
                ? SparkWitch.id(parsed.getPath())
                : parsed;
    }

    private static Role requireRole(Identifier id) throws CommandSyntaxException {
        for (PromotionRole promotionRole : PROMOTION_ROLES) {
            if (promotionRole.id().equals(id)) {
                return promotionRole.role().get();
            }
        }
        throw UNKNOWN_ROLE.create(id);
    }

    static CompletableFuture<Suggestions> suggestPromotionRoles(
            CommandContext<ServerCommandSource> context,
            SuggestionsBuilder builder
    ) {
        for (PromotionRole promotionRole : PROMOTION_ROLES) {
            String suggestion = promotionRole.id().getPath();
            if (CommandSource.shouldSuggest(builder.getRemaining(), suggestion)) {
                builder.suggest(suggestion);
            }
        }
        return builder.buildFuture();
    }

    private record PromotionRole(Identifier id, Supplier<Role> role) {
    }
}
