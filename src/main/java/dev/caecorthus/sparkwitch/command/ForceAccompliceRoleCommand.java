package dev.caecorthus.sparkwitch.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.component.WitchWorldComponent;
import dev.caecorthus.sparkwitch.roles.witch.accomplice.variant.AccompliceVariants;
import dev.caecorthus.sparkwitch.roles.witch.bewitched.BewitchedPromotionRules;
import dev.caecorthus.sparkwitch.roles.witch.bewitched.BewitchedPromotionService;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * Registers {@code /sparkwitch:forceAccompliceRole <role> <players>} (D4, C5): locks the role each target's Bewitched
 * promotion yields, the plain Accomplice or one registered special accomplice. A living Bewitched of an ACTIVE round is
 * locked for the current round; any other target (lobby, start or end fade, not a living Bewitched) is locked for the
 * next round, which keeps the lock only if the target is then a Bewitched, the same lifetime as Wathe's forced roles
 * (owner, 2026-10-10). A special accomplice is locked to one player at a time per round; the plain Accomplice to any
 * number. Locks are server-only.
 * 注册 {@code /sparkwitch:forceAccompliceRole <role> <players>}（D4、C5）：锁定每名目标魔化使晋升后的身份，可以是普通共犯或
 * 一种已注册的特殊共犯。ACTIVE 对局中存活的魔化使锁定于本局；其他目标（大厅、开局或结束渐变、不是存活魔化使）锁定于下一局，
 * 且仅当目标届时是魔化使才保留，生命周期与 Wathe 强制身份相同（所有者 2026-10-10）。特殊共犯每局同一时间只能锁定给一名
 * 玩家；普通共犯不限人数。锁定仅保存在服务端。
 */
public final class ForceAccompliceRoleCommand {
    static final String KEY_PREFIX = "command.sparkwitch.force_accomplice_role.";
    private static final DynamicCommandExceptionType UNKNOWN_ROLE = new DynamicCommandExceptionType(
            id -> Text.translatable(KEY_PREFIX + "unknown_role", id));
    private static final DynamicCommandExceptionType SPECIAL_SINGLE_TARGET = new DynamicCommandExceptionType(
            role -> Text.translatable(KEY_PREFIX + "special_single_target", role));
    private static final Dynamic2CommandExceptionType ALREADY_LOCKED = new Dynamic2CommandExceptionType(
            (holder, role) -> Text.translatable(KEY_PREFIX + "already_locked", holder, role));

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
                        .then(CommandManager.argument("players", EntityArgumentType.players())
                                .executes(context -> execute(
                                        context.getSource(),
                                        requireRole(ForcePromotionCommand.normalizeRoleId(
                                                IdentifierArgumentType.getIdentifier(context, "role"))),
                                        EntityArgumentType.getPlayers(context, "players")
                                )))));
    }

    private static int execute(
            ServerCommandSource source,
            Role role,
            Collection<ServerPlayerEntity> players
    ) throws CommandSyntaxException {
        MinecraftServer server = source.getServer();
        // Locks live on the overworld component, like forced Wraith promotions; the round is read where it runs.
        // 锁定与强制冤魂晋升一样保存在主世界组件上；对局状态从其实际所在的世界读取。
        WitchWorldComponent world = WitchWorldComponent.KEY.get(server.getOverworld());
        GameWorldComponent game = GameWorldComponent.KEY.get(gameWorld(server));
        boolean active = game.getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
        boolean special = AccompliceVariants.isVariant(role);
        Text roleName = roleName(role);
        Set<UUID> targets = new HashSet<>();
        List<UUID> currentRound = new ArrayList<>();
        List<UUID> nextRound = new ArrayList<>();
        for (ServerPlayerEntity player : players) {
            UUID uuid = player.getUuid();
            if (targets.add(uuid)) {
                (BewitchedPromotionRules.appliesToCurrentRound(active,
                        BewitchedPromotionService.isLivingBewitched(game, uuid)) ? currentRound : nextRound).add(uuid);
            }
        }
        List<UUID> staleHolders = new ArrayList<>();
        // One holder per special accomplice per round: a current-round holder still counts only while it is a living
        // Bewitched; a next-round holder always counts. / 每局每个特殊共犯只有一名持有者：本局持有者仅在仍是存活魔化使时
        // 计入；下一局持有者始终计入。
        UUID holder = !special ? null : currentRound.isEmpty()
                ? otherHolder(world.getPendingForcedAccompliceRoles(), role, targets, uuid -> true, staleHolders)
                : otherHolder(world.getForcedAccompliceRoles(), role, targets,
                        uuid -> BewitchedPromotionService.isLivingBewitched(game, uuid), staleHolders);
        switch (BewitchedPromotionRules.checkLock(special, targets.size(), holder != null)) {
            case SPECIAL_SINGLE_TARGET -> throw SPECIAL_SINGLE_TARGET.create(roleName);
            case ALREADY_LOCKED -> throw ALREADY_LOCKED.create(holderName(server, holder), roleName);
            case OK -> {
            }
        }
        return Wathe.executeSupporterCommand(source, () -> {
            // A stale holder's seat is already back in the pool (D4); drop its lock so only one player holds it.
            // 失效持有者的席位已回到池中（D4）；移除其锁定，保证只有一名玩家持有。
            staleHolders.forEach(world::clearForcedAccompliceRole);
            currentRound.forEach(uuid -> world.setForcedAccompliceRole(uuid, role.identifier()));
            nextRound.forEach(uuid -> world.setPendingForcedAccompliceRole(uuid, role.identifier()));
            if (!currentRound.isEmpty()) {
                source.sendFeedback(() -> Text.translatable(
                        KEY_PREFIX + "success.current_round", currentRound.size(), roleName), true);
            }
            if (!nextRound.isEmpty()) {
                source.sendFeedback(() -> Text.translatable(
                        KEY_PREFIX + "success.next_round", nextRound.size(), roleName), true);
            }
        });
    }

    /**
     * The other player still holding this special accomplice's lock in {@code locks}, if any; holders that
     * {@code stillHeld} rejects (a current-round holder who is no longer a living Bewitched: dead, promoted) are
     * collected into {@code staleHolders} instead (C5, D4).
     * {@code locks} 中仍持有该特殊共犯锁定的其他玩家（若有）；被 {@code stillHeld} 拒绝的持有者（不再是存活魔化使的本局持有者：
     * 已死亡、已晋升）改为收集到 {@code staleHolders}（C5、D4）。
     */
    private static @Nullable UUID otherHolder(Map<UUID, Identifier> locks, Role role, Set<UUID> targets,
                                              Predicate<UUID> stillHeld, List<UUID> staleHolders) {
        UUID holder = null;
        for (Map.Entry<UUID, Identifier> lock : locks.entrySet()) {
            if (targets.contains(lock.getKey()) || !role.identifier().equals(lock.getValue())) {
                continue;
            }
            if (stillHeld.test(lock.getKey())) {
                holder = holder == null ? lock.getKey() : holder;
            } else {
                staleHolders.add(lock.getKey());
            }
        }
        return holder;
    }

    /**
     * The world whose round runs (map voting may move it off the overworld), else the overworld.
     * 正在进行对局的世界（地图投票可能使其不在主世界），否则为主世界。
     */
    private static ServerWorld gameWorld(MinecraftServer server) {
        for (ServerWorld candidate : server.getWorlds()) {
            if (GameWorldComponent.KEY.get(candidate).isRunning()) {
                return candidate;
            }
        }
        return server.getOverworld();
    }

    private static Text holderName(MinecraftServer server, UUID holder) {
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(holder);
        return online != null ? online.getName() : Text.literal(holder.toString());
    }

    private static Text roleName(Role role) {
        return Text.translatable(RoleDisplayTextRules.roleTranslationKey(role));
    }

    /** The plain Accomplice or a registered special accomplice. / 普通共犯或已注册的特殊共犯。 */
    static Role requireRole(Identifier id) throws CommandSyntaxException {
        Role role = BewitchedPromotionRules.resolveLock(id, SparkWitchRoles.accomplice(),
                AccompliceVariants.variants());
        if (role == null) {
            throw UNKNOWN_ROLE.create(id);
        }
        return role;
    }

    /** {@code accomplice}, then every registered special accomplice. / 先普通共犯，再每个已注册的特殊共犯。 */
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
