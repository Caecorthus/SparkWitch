package dev.caecorthus.sparkwitch.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.Dynamic3CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.component.ForcedRecruitPlan;
import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import dev.caecorthus.sparkwitch.roles.witch.accomplice.variant.AccompliceVariants;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment.ForcedRecruit;
import dev.caecorthus.sparkwitch.roles.witch.grandwitch.recruitment.GrandWitchRecruitmentRoundComponent;
import dev.caecorthus.sparkwitch.util.RoleDisplayTextRules;
import dev.caecorthus.sparkwitch.util.SparkWitchPermissions;
import dev.doctor4t.wathe.Wathe;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.UserCache;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Registers {@code /sparkwitch:forceAccompliceRole <role> <player> [order]}: pre-decides the Grand Witch's
 * {@code order}-th successful recruitment of a round (default: the next free pending one) to take {@code player} as
 * {@code role}, the plain Accomplice or a registered special accomplice. Set between rounds it applies to the next
 * round; set during a round it applies to the current one. The recruitment service honours it only if that player is
 * still alive then. Entries live on the overworld {@code WitchWorldComponent} and are cleared when a round finalizes.
 * 注册 {@code /sparkwitch:forceAccompliceRole <role> <player> [order]}：预先决定本局大魔女第 {@code order} 次成功招募
 * （默认取下一个空闲的待生效序号）选中 {@code player}，并赋予 {@code role}（普通共犯或已注册的特殊共犯）。对局之间设置
 * 作用于下一局；对局中设置作用于本局。招募服务只在届时该玩家仍存活时采用。条目保存在主世界 {@code WitchWorldComponent}
 * 上，并在对局结束（finalize）时清空。
 */
public final class ForceAccompliceRoleCommand {
    static final String KEY_PREFIX = "command.sparkwitch.force_accomplice_role.";
    private static final DynamicCommandExceptionType UNKNOWN_ROLE = new DynamicCommandExceptionType(
            id -> Text.translatable(KEY_PREFIX + "unknown_role", id));
    private static final Dynamic2CommandExceptionType ORDER_PASSED = new Dynamic2CommandExceptionType(
            (order, recruited) -> Text.translatable(KEY_PREFIX + "order_passed", order, recruited));
    private static final Dynamic3CommandExceptionType SPECIAL_HELD = new Dynamic3CommandExceptionType(
            (holder, role, order) -> Text.translatable(KEY_PREFIX + "special_held", holder, role, order));

    private static final SimpleCommandExceptionType ROUND_ENDING = new SimpleCommandExceptionType(
            Text.translatable(KEY_PREFIX + "round_ending"));

    private ForceAccompliceRoleCommand() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("sparkwitch:forceAccompliceRole")
                .requires(Permissions.require(
                        SparkWitchPermissions.COMMAND_FORCE_ACCOMPLICE_ROLE,
                        SparkWitchPermissions.DEFAULT_COMMAND_LEVEL
                ))
                .then(CommandManager.argument("role", IdentifierArgumentType.identifier())
                        .suggests(ForceAccompliceRoleCommand::suggestRoles)
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(context -> execute(context, null))
                                .then(CommandManager.argument("order", IntegerArgumentType.integer(1))
                                        .executes(context -> execute(
                                                context, IntegerArgumentType.getInteger(context, "order")))))));
    }

    private static int execute(CommandContext<ServerCommandSource> context, @Nullable Integer order)
            throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        Role role = requireRole(ForcePromotionCommand.normalizeRoleId(
                IdentifierArgumentType.getIdentifier(context, "role")));
        ServerPlayerEntity player = EntityArgumentType.getPlayer(context, "player");
        MinecraftServer server = source.getServer();
        // Entries live on the overworld store, like forced Wraith promotions; the round is read where it runs.
        // 条目与强制冤魂晋升一样保存在主世界存储上；对局状态从其实际所在的世界读取。
        WitchWorldComponent store = WitchWorldComponent.KEY.get(server.getOverworld());
        ServerWorld gameWorld = runningGameWorld(server);
        // The end screen still counts as running, but finalize wipes every entry right after; refuse instead of
        // reporting "current round" for an entry that can never fire (review 2026-10-06).
        // 结算画面仍算对局进行中，但随后的 finalize 会清空所有条目；拒绝设置，而不是对永远不会生效的条目提示"本局"
        // （2026-10-06 评审）。
        if (gameWorld != null
                && GameWorldComponent.KEY.get(gameWorld).getGameStatus() == GameWorldComponent.GameStatus.STOPPING) {
            throw ROUND_ENDING.create();
        }
        boolean currentRound = gameWorld != null;
        int recruited = currentRound ? GrandWitchRecruitmentRoundComponent.KEY.get(gameWorld).getRecruitedCount() : 0;
        Text roleName = roleName(role.identifier());
        ForcedRecruitPlan.Accepted plan = switch (store.planForcedRecruit(
                new ForcedRecruit(player.getUuid(), role.identifier()),
                AccompliceVariants.isVariant(role),
                order,
                recruited
        )) {
            case ForcedRecruitPlan.OrderPassed passed -> throw ORDER_PASSED.create(passed.order(), passed.recruitedCount());
            case ForcedRecruitPlan.SpecialHeld held ->
                    throw SPECIAL_HELD.create(playerName(server, held.holder().player()), roleName, held.holderOrder());
            case ForcedRecruitPlan.Accepted accepted -> accepted;
        };
        return Wathe.executeSupporterCommand(source, () -> {
            store.applyForcedRecruit(plan);
            source.sendFeedback(() -> Text.translatable(
                    KEY_PREFIX + (currentRound ? "success.current_round" : "success.next_round"),
                    player.getName(),
                    roleName,
                    plan.order()
            ), true);
            if (plan.moved()) {
                source.sendFeedback(() -> Text.translatable(
                        KEY_PREFIX + "moved", player.getName(), plan.movedFrom()), true);
            }
            ForcedRecruit replaced = plan.replaced();
            if (replaced != null) {
                source.sendFeedback(() -> Text.translatable(
                        KEY_PREFIX + "replaced",
                        playerName(server, replaced.player()),
                        roleName(replaced.role()),
                        plan.order()
                ), true);
            }
        });
    }

    /**
     * The world whose round runs (map voting may move it off the overworld), or null between rounds.
     * 正在进行对局的世界（地图投票可能使其不在主世界），对局之间为 null。
     */
    private static @Nullable ServerWorld runningGameWorld(MinecraftServer server) {
        for (ServerWorld candidate : server.getWorlds()) {
            if (GameWorldComponent.KEY.get(candidate).isRunning()) {
                return candidate;
            }
        }
        return null;
    }

    private static Text playerName(MinecraftServer server, UUID uuid) {
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        UserCache cache = server.getUserCache();
        String cached = cache == null ? null : cache.getByUuid(uuid).map(GameProfile::getName).orElse(null);
        return Text.literal(cached != null ? cached : uuid.toString());
    }

    private static Text roleName(Identifier roleId) {
        return Text.translatable(RoleDisplayTextRules.roleTranslationKey(roleId.getPath()));
    }

    /**
     * The plain Accomplice or a registered special accomplice; anything else is refused.
     * 普通共犯或已注册的特殊共犯；其他职业一律拒绝。
     */
    static Role requireRole(Identifier id) throws CommandSyntaxException {
        Role role = resolveRole(id, SparkWitchRoles.accomplice(), AccompliceVariants.variants());
        if (role == null) {
            throw UNKNOWN_ROLE.create(id);
        }
        return role;
    }

    static @Nullable Role resolveRole(Identifier id, Role plainAccomplice, List<Role> variants) {
        if (id.equals(plainAccomplice.identifier())) {
            return plainAccomplice;
        }
        for (Role variant : variants) {
            if (id.equals(variant.identifier())) {
                return variant;
            }
        }
        return null;
    }

    /**
     * {@code accomplice}, then every registered special accomplice (the sparkwitch namespace without its prefix).
     * 先 {@code accomplice}，再每个已注册的特殊共犯（sparkwitch 命名空间省略前缀）。
     */
    static List<String> suggestionIds() {
        List<String> ids = new ArrayList<>();
        ids.add(suggestionId(SparkWitchRoles.ACCOMPLICE_ID));
        for (Role variant : AccompliceVariants.variants()) {
            ids.add(suggestionId(variant.identifier()));
        }
        return ids;
    }

    private static String suggestionId(Identifier id) {
        return SparkWitch.MOD_ID.equals(id.getNamespace()) ? id.getPath() : id.toString();
    }

    static CompletableFuture<Suggestions> suggestRoles(
            CommandContext<ServerCommandSource> context,
            SuggestionsBuilder builder
    ) {
        for (String suggestion : suggestionIds()) {
            if (CommandSource.shouldSuggest(builder.getRemaining(), suggestion)) {
                builder.suggest(suggestion);
            }
        }
        return builder.buildFuture();
    }
}
