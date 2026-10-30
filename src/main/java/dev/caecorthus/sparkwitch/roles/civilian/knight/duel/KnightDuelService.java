package dev.caecorthus.sparkwitch.roles.civilian.knight.duel;

import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.SparkWitchSounds;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.util.ActionResult;
import dev.caecorthus.sparkwitch.compat.KnightTraitsBridge;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownManagerAccessor;
import dev.caecorthus.sparkwitch.net.KnightDuelStateS2CPayload;
import dev.caecorthus.sparkwitch.net.KnightDuelStopSoundS2CPayload;
import dev.caecorthus.sparkwitch.registry.SparkWitchDataComponents;
import dev.caecorthus.sparkwitch.roles.civilian.knight.KnightRules;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.item.CocktailItem;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server owner of the Knight duel: validation, the Manba Out branch, duel state, teams/effects, item cooldowns,
 * marked knife, titles, audio and cleanup.
 * 骑士决斗的服务端归属：校验、曼巴out 分支、决斗状态、队伍/效果、物品冷却、标记刀、标题、音频与收尾。
 */
public final class KnightDuelService {
    private static boolean registered;

    private KnightDuelService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(KnightDuelService::resetDuelTeams);
        KillPlayer.AFTER.register(KnightDuelService::afterKill);
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }
            KnightDuelWorldComponent duel = KnightDuelWorldComponent.KEY.get(serverPlayer.getWorld());
            if (!duel.isDuelist(serverPlayer.getUuid())) {
                return ActionResult.PASS;
            }
            ItemStack held = serverPlayer.getStackInHand(hand);
            if (held.isOf(SparkWitchItems.ceremonialSword()) || held.isOf(WatheItems.BAT)) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (handler.player.getWorld() instanceof ServerWorld world) {
                KnightDuelWorldComponent duel = KnightDuelWorldComponent.KEY.get(world);
                if (duel.active()) {
                    ServerPlayNetworking.send(handler.player, statePayload(duel));
                }
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.player;
            if (player.getWorld() instanceof ServerWorld world) {
                KnightDuelWorldComponent duel = KnightDuelWorldComponent.KEY.get(world);
                if (duel.isDuelist(player.getUuid())) {
                    endDuel(world, duel);
                }
            }
        });
        GameEvents.ON_FINISH_INITIALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                resetDuelTeams(serverWorld.getServer());
                KnightDuelWorldComponent duel = KnightDuelWorldComponent.KEY.get(serverWorld);
                if (duel.active()) {
                    endDuel(serverWorld, duel);
                }
            }
        });
        GameEvents.ON_FINISH_FINALIZE.register((world, game) -> {
            if (world instanceof ServerWorld serverWorld) {
                KnightDuelWorldComponent duel = KnightDuelWorldComponent.KEY.get(serverWorld);
                if (duel.active()) {
                    endDuel(serverWorld, duel);
                }
            }
        });
    }

    /** C2S entry: the client only proposes; every rule is re-checked here. / C2S 入口：客户端只提议，所有规则在此重查。 */
    public static void requestDuel(ServerPlayerEntity knight, UUID targetId) {
        if (knight == null || targetId == null) {
            return;
        }
        ServerWorld world = knight.getServerWorld();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        KnightDuelWorldComponent duel = KnightDuelWorldComponent.KEY.get(world);
        if (duel.active() || game.getGameStatus() != GameWorldComponent.GameStatus.ACTIVE
                || !KnightRules.isKnight(game.getRole(knight))
                || !GameFunctions.isPlayerPlayingAndAlive(knight)) {
            return;
        }
        ServerPlayerEntity target = world.getServer().getPlayerManager().getPlayer(targetId);
        if (target == null || target == knight || !GameFunctions.isPlayerPlayingAndAlive(target)) {
            return;
        }
        if (PlayerShopComponent.KEY.get(knight).getBalance() < KnightDuelRules.COST) {
            return;
        }
        boolean good = game.isInnocent(target);
        boolean impostor = Boolean.TRUE.equals(KnightTraitsBridge.isImpostorActive(target));
        if (good && !impostor) {
            GameFunctions.killPlayer(knight, true, null, SparkWitchDeathReasons.MANBA_OUT);
            playSoundToAll(world.getServer(), SparkWitchSounds.DUEL_MANBA);
            if (isSurvivalMaster(game, target)) {
                showTitleToAll(world.getServer(),
                        Text.translatable("title.sparkwitch.duel.survival").formatted(Formatting.WHITE));
            }
            return;
        }
        startDuel(world, duel, knight, target);
    }

    private static void startDuel(ServerWorld world, KnightDuelWorldComponent duel,
                                  ServerPlayerEntity knight, ServerPlayerEntity target) {
        PlayerShopComponent shop = PlayerShopComponent.KEY.get(knight);
        shop.setBalance(shop.getBalance() - KnightDuelRules.COST);
        duel.begin(knight.getUuid(), target.getUuid(), world.getTime() + KnightDuelRules.DUEL_TICKS);
        broadcastState(world.getServer(), duel);
        applyTeamsAndEffects(world, duel, knight, target);
        applyCooldowns(knight, target, duel);
        giveMarkedKnifeIfMissing(target);
        stopSf(world);
        playSoundToAll(world.getServer(), SparkWitchSounds.DUEL_SF);
        showTitleToAll(world.getServer(), Text.translatable("title.sparkwitch.duel.start", target.getNameForScoreboard())
                .formatted(Formatting.WHITE));
    }

    static void tick(ServerWorld world, KnightDuelWorldComponent duel) {
        if (!duel.active()) {
            return;
        }
        if (duel.remainingTicks() <= 0) {
            UUID knightId = duel.knight();
            endDuel(world, duel);
            ServerPlayerEntity knight = knightId == null ? null : world.getServer().getPlayerManager().getPlayer(knightId);
            if (knight != null && GameFunctions.isPlayerPlayingAndAlive(knight)) {
                GameFunctions.killPlayer(knight, true, null, SparkWitchDeathReasons.DUEL_TIMEOUT);
            }
            return;
        }
        if (world.getTime() % 10 == 0) {
            // Every 0.5 s: force-end if a duelist died without a kill event, then re-apply the duel state.
            // 每 0.5 秒：若有一方死亡且未走击杀事件，强制结束；随后重新下发决斗状态。
            GameWorldComponent game = GameWorldComponent.KEY.get(world);
            UUID knightId = duel.knight();
            UUID targetId = duel.target();
            if ((knightId != null && game.isPlayerDead(knightId))
                    || (targetId != null && game.isPlayerDead(targetId))) {
                endDuel(world, duel);
                return;
            }
            ServerPlayerEntity target = player(world.getServer(), targetId);
            if (target != null) {
                applyCooldowns(target, target, duel);
            }
            duel.sync();
            broadcastState(world.getServer(), duel);
        }
    }

    private static void afterKill(ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer, Identifier reason) {
        if (!(victim.getWorld() instanceof ServerWorld world)) {
            return;
        }
        KnightDuelWorldComponent duel = KnightDuelWorldComponent.KEY.get(world);
        if (!duel.active() || !duel.isDuelist(victim.getUuid())) {
            return;
        }
        UUID otherId = duel.isKnight(victim.getUuid()) ? duel.target() : duel.knight();
        // Being pushed off the train is a normal duel end, never a third-party violation (owner, 2026-10-30).
        // 被推下列车属于正常结束，不算第三方违反规则（所有者，2026-10-30）。
        boolean violation = killer != null && !duel.isDuelist(killer.getUuid())
                && !GameConstants.DeathReasons.FELL_OUT_OF_TRAIN.equals(reason);
        endDuel(world, duel);
        if (violation && otherId != null) {
            ServerPlayerEntity other = world.getServer().getPlayerManager().getPlayer(otherId);
            if (other != null && GameFunctions.isPlayerPlayingAndAlive(other)) {
                GameFunctions.killPlayer(other, true, null, SparkWitchDeathReasons.DUEL_VIOLATION);
            }
        }
    }

    static void endDuel(ServerWorld world, KnightDuelWorldComponent duel) {
        if (!duel.active()) {
            return;
        }
        UUID knightId = duel.knight();
        UUID targetId = duel.target();
        int elapsed = KnightDuelRules.DUEL_TICKS - duel.remainingTicks();
        Map<UUID, StatusEffectInstance> previousGlowing = Map.copyOf(duel.previousGlowing());
        Map<UUID, StatusEffectInstance> previousSpeed = Map.copyOf(duel.previousSpeed());
        Map<UUID, String> previousTeams = Map.copyOf(duel.previousTeams());
        duel.clearState();
        duel.sync();
        broadcastState(world.getServer(), duel);
        stopSf(world);
        cleanupPlayer(world, knightId, elapsed, previousGlowing, previousSpeed, previousTeams);
        cleanupPlayer(world, targetId, elapsed, previousGlowing, previousSpeed, previousTeams);
    }

    private static void cleanupPlayer(ServerWorld world, @Nullable UUID uuid, int elapsed,
                                      Map<UUID, StatusEffectInstance> previousGlowing,
                                      Map<UUID, StatusEffectInstance> previousSpeed,
                                      Map<UUID, String> previousTeams) {
        if (uuid == null) {
            return;
        }
        ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
        if (player == null) {
            return;
        }
        restoreEffect(player, StatusEffects.GLOWING, previousGlowing.get(uuid), elapsed);
        restoreEffect(player, StatusEffects.SPEED, previousSpeed.get(uuid), elapsed);
        restoreTeam(world.getScoreboard(), player, previousTeams.get(uuid));
        removeMarkedKnives(player);
        clearCooldowns(player);
    }

    private static void restoreEffect(ServerPlayerEntity player, RegistryEntry<StatusEffect> effect,
                                      @Nullable StatusEffectInstance previous, int elapsed) {
        player.removeStatusEffect(effect);
        if (previous == null) {
            return;
        }
        int remaining = Math.max(0, previous.getDuration() - elapsed);
        if (remaining > 0) {
            player.addStatusEffect(new StatusEffectInstance(effect, remaining, previous.getAmplifier(),
                    previous.isAmbient(), previous.shouldShowParticles(), previous.shouldShowIcon()));
        }
    }

    private static void removeMarkedKnives(ServerPlayerEntity player) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!stack.isEmpty() && Boolean.TRUE.equals(stack.get(SparkWitchDataComponents.DUEL_KNIFE))) {
                inventory.setStack(slot, ItemStack.EMPTY);
            }
        }
        inventory.markDirty();
        player.currentScreenHandler.sendContentUpdates();
    }

    private static void clearCooldowns(ServerPlayerEntity player) {
        ItemCooldownManager manager = player.getItemCooldownManager();
        Map<Item, ?> entries = ((ItemCooldownManagerAccessor) manager).sparkwitch$getEntries();
        for (Item item : new ArrayList<>(entries.keySet())) {
            manager.remove(item);
        }
    }

    private static void applyTeamsAndEffects(ServerWorld world, KnightDuelWorldComponent duel,
                                             ServerPlayerEntity knight, ServerPlayerEntity target) {
        Scoreboard scoreboard = world.getScoreboard();
        Team knightTeam = team(scoreboard, KnightDuelRules.KNIGHT_TEAM, KnightDuelRules.KNIGHT_COLOR);
        Team targetTeam = team(scoreboard, KnightDuelRules.TARGET_TEAM, KnightDuelRules.TARGET_COLOR);
        joinTeam(scoreboard, duel, knight, knightTeam);
        joinTeam(scoreboard, duel, target, targetTeam);
        applyEffect(duel, knight, StatusEffects.GLOWING, 0, duel.previousGlowing());
        applyEffect(duel, knight, StatusEffects.SPEED, 1, duel.previousSpeed());
        applyEffect(duel, target, StatusEffects.GLOWING, 0, duel.previousGlowing());
    }

    private static Team team(Scoreboard scoreboard, String name, Formatting color) {
        Team team = scoreboard.getTeam(name);
        if (team == null) {
            team = scoreboard.addTeam(name);
        }
        team.setColor(color);
        team.setFriendlyFireAllowed(true);
        // Normal solid player collision: never leave a persisted NEVER rule from older builds.
        // 正常实体碰撞：清除旧版本可能残留在存档里的 NEVER 队伍规则。
        team.setCollisionRule(AbstractTeam.CollisionRule.ALWAYS);
        return team;
    }

    private static void joinTeam(Scoreboard scoreboard, KnightDuelWorldComponent duel,
                                 ServerPlayerEntity player, Team duelTeam) {
        String name = player.getNameForScoreboard();
        Team previous = scoreboard.getScoreHolderTeam(name);
        if (previous != null) {
            duel.previousTeams().put(player.getUuid(), previous.getName());
            scoreboard.removeScoreHolderFromTeam(name, previous);
        }
        scoreboard.addScoreHolderToTeam(name, duelTeam);
    }

    private static void restoreTeam(Scoreboard scoreboard, ServerPlayerEntity player, @Nullable String previousName) {
        String name = player.getNameForScoreboard();
        Team current = scoreboard.getScoreHolderTeam(name);
        if (current != null && (current.getName().equals(KnightDuelRules.KNIGHT_TEAM)
                || current.getName().equals(KnightDuelRules.TARGET_TEAM))) {
            scoreboard.removeScoreHolderFromTeam(name, current);
        }
        if (previousName != null) {
            Team previous = scoreboard.getTeam(previousName);
            if (previous != null) {
                scoreboard.addScoreHolderToTeam(name, previous);
            }
        }
    }

    private static void applyEffect(KnightDuelWorldComponent duel, ServerPlayerEntity player,
                                    RegistryEntry<StatusEffect> effect, int amplifier,
                                    Map<UUID, StatusEffectInstance> previous) {
        StatusEffectInstance existing = player.getStatusEffect(effect);
        if (existing != null) {
            previous.put(player.getUuid(), new StatusEffectInstance(existing));
        }
        player.removeStatusEffect(effect);
        player.addStatusEffect(new StatusEffectInstance(effect, KnightDuelRules.DUEL_TICKS, amplifier));
    }

    private static void applyCooldowns(ServerPlayerEntity knight, ServerPlayerEntity target,
                                       KnightDuelWorldComponent duel) {
        knight.getItemCooldownManager().set(WatheItems.REVOLVER, KnightDuelRules.REVOLVER_COOLDOWN_TICKS);
        for (int slot = 0; slot < target.getInventory().size(); slot++) {
            ItemStack stack = target.getInventory().getStack(slot);
            if (stack.isEmpty() || whitelisted(stack)) {
                continue;
            }
            Item item = stack.getItem();
            if (duel.cooledItems().add(item)) {
                target.getItemCooldownManager().set(item, KnightDuelRules.ITEM_COOLDOWN_TICKS);
            }
        }
    }

    private static boolean whitelisted(ItemStack stack) {
        Item item = stack.getItem();
        if (item == WatheItems.LETTER || item == WatheItems.KEY || item == WatheItems.LOCKPICK
                || item == WatheItems.CROWBAR || item == WatheItems.KNIFE || item == WatheItems.DERRINGER
                || item == SparkWitchItems.crossSword() || item instanceof CocktailItem) {
            return true;
        }
        Identifier id = Registries.ITEM.getId(item);
        if (id.equals(KnightDuelRules.MASTER_KEY_ID) || id.equals(KnightDuelRules.NEUTRAL_MASTER_KEY_ID)) {
            return true;
        }
        if (!id.getNamespace().equals("minecraft")) {
            return false;
        }
        boolean weaponOrTool = stack.isIn(ItemTags.SWORDS) || stack.isIn(ItemTags.AXES)
                || stack.isIn(ItemTags.PICKAXES) || stack.isIn(ItemTags.SHOVELS) || stack.isIn(ItemTags.HOES)
                || item == Items.BOW || item == Items.CROSSBOW || item == Items.TRIDENT || item == Items.MACE;
        return !weaponOrTool;
    }

    private static void giveMarkedKnifeIfMissing(ServerPlayerEntity target) {
        for (int slot = 0; slot < target.getInventory().size(); slot++) {
            if (target.getInventory().getStack(slot).isOf(WatheItems.KNIFE)) {
                return;
            }
        }
        ItemStack knife = new ItemStack(WatheItems.KNIFE);
        knife.set(SparkWitchDataComponents.DUEL_KNIFE, true);
        target.giveItemStack(knife);
    }

    private static boolean isSurvivalMaster(GameWorldComponent game, ServerPlayerEntity player) {
        Role role = game.getRole(player);
        return role != null && KnightDuelRules.SURVIVAL_MASTER_ID.equals(role.identifier());
    }

    /** Clears old NEVER rules/memberships persisted by earlier builds. / 清理旧版本残留在存档里的 NEVER 规则与成员。 */
    private static void resetDuelTeams(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            Scoreboard scoreboard = world.getScoreboard();
            for (String name : List.of(KnightDuelRules.KNIGHT_TEAM, KnightDuelRules.TARGET_TEAM)) {
                Team team = scoreboard.getTeam(name);
                if (team == null) {
                    continue;
                }
                team.setCollisionRule(AbstractTeam.CollisionRule.ALWAYS);
                for (String member : List.copyOf(team.getPlayerList())) {
                    scoreboard.removeScoreHolderFromTeam(member, team);
                }
            }
        }
    }

    private static @Nullable ServerPlayerEntity player(MinecraftServer server, @Nullable UUID uuid) {
        return uuid == null ? null : server.getPlayerManager().getPlayer(uuid);
    }

    private static KnightDuelStateS2CPayload statePayload(KnightDuelWorldComponent duel) {
        if (!duel.active() || duel.knight() == null || duel.target() == null) {
            return KnightDuelStateS2CPayload.inactive();
        }
        return new KnightDuelStateS2CPayload(true, duel.knight(), duel.target(), duel.remainingTicks());
    }

    private static void broadcastState(MinecraftServer server, KnightDuelWorldComponent duel) {
        KnightDuelStateS2CPayload payload = statePayload(duel);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private static void playSoundToAll(MinecraftServer server, SoundEvent sound) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.playSoundToPlayer(sound, SoundCategory.MASTER, 1.0F, 1.0F);
        }
    }

    private static void stopSf(ServerWorld world) {
        KnightDuelStopSoundS2CPayload payload = new KnightDuelStopSoundS2CPayload();
        for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private static void showTitleToAll(MinecraftServer server, Text text) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.networkHandler.sendPacket(new TitleFadeS2CPacket(
                    KnightDuelRules.TITLE_FADE_IN, KnightDuelRules.TITLE_STAY, KnightDuelRules.TITLE_FADE_OUT));
            player.networkHandler.sendPacket(new TitleS2CPacket(text));
        }
    }
}
