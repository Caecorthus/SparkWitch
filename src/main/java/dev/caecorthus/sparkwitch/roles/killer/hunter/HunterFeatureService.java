package dev.caecorthus.sparkwitch.roles.killer.hunter;

import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownEntryAccessor;
import dev.caecorthus.sparkwitch.mixin.accessor.ItemCooldownManagerAccessor;
import dev.doctor4t.wathe.api.Faction;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.api.event.ShouldDropOnDeath;
import dev.doctor4t.wathe.api.event.ShouldPunishGunShooter;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerPoisonComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.index.WatheItems;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/** Wires Hunter-only interactions, economy, cleanup, and replay behavior into Wathe events. */
public final class HunterFeatureService {
    private static final int DISMANTLE_COOLDOWN_TICKS = 15 * 20;
    private static boolean registered;

    private HunterFeatureService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;

        HunterShopService.register();
        UseBlockCallback.EVENT.register(HunterFeatureService::interactWithTrap);
        KillPlayer.AFTER.register(HunterFeatureService::afterConfirmedDeath);
        ShouldDropOnDeath.EVENT.register((stack, victim) -> stack.getItem() instanceof HunterTrapItem);
        ShouldPunishGunShooter.EVENT.register(HunterFeatureService::gunPunishment);
        ResetPlayer.EVENT.register(player -> HunterPlayerComponent.KEY.get(player).reset());
        RoleAssigned.EVENT.register((player, role) -> HunterPlayerComponent.KEY.get(player).reset());
        GameEvents.ON_WIN_DETERMINED.register((world, component, status, neutralWinner) -> cleanupRound(world));
        GameEvents.ON_FINISH_FINALIZE.register((world, component) -> {
            if (world instanceof ServerWorld serverWorld) {
                cleanupRound(serverWorld);
            }
        });
        registerReplayFormatters();
    }

    private static ActionResult interactWithTrap(
            PlayerEntity player,
            World world,
            Hand hand,
            net.minecraft.util.hit.BlockHitResult hitResult
    ) {
        if (hand != Hand.MAIN_HAND) {
            return ActionResult.PASS;
        }
        HunterTrapEntity trap = nearestTrap(world, hitResult);
        if (trap == null) {
            return ActionResult.PASS;
        }

        if (player.isSneaking() && player.getUuid().equals(trap.getOwnerUuid())) {
            if (!world.isClient) {
                ItemStack returnedTrap = new ItemStack(Registries.ITEM.get(HunterTrapItem.ID));
                if (!player.giveItemStack(returnedTrap)) {
                    player.dropItem(returnedTrap, false);
                }
                recordTrapInteraction(player, trap, "pickup");
                world.playSound(
                        null,
                        trap.getBlockPos(),
                        SoundEvents.BLOCK_CHAIN_FALL,
                        SoundCategory.PLAYERS,
                        0.8F,
                        1.2F
                );
                trap.discardTrap();
            }
            return ActionResult.SUCCESS;
        }

        Role role = GameWorldComponent.KEY.get(world).getRole(player);
        // Dismantlers have direct-view access only; owner reclaim above intentionally remains unrestricted.
        // 拆除者只能直视操作；上方放置者回收路径有意不受此限制。
        if (player.isSneaking() && role != null
                && HunterRules.canDismantle(role.identifier(), player.canSee(trap))) {
            if (!world.isClient) {
                applyDismantleCooldown(player);
                recordTrapInteraction(player, trap, "dismantle");
                world.playSound(
                        null,
                        trap.getBlockPos(),
                        SoundEvents.BLOCK_CHAIN_FALL,
                        SoundCategory.PLAYERS,
                        0.8F,
                        1.2F
                );
                trap.discardTrap();
            }
            return ActionResult.SUCCESS;
        }

        ItemStack heldStack = player.getStackInHand(hand);
        if (!heldStack.isOf(WatheItems.POISON_VIAL)
                || role == null
                || role.getFaction() != Faction.KILLER
                || trap.isPoisoned()) {
            return ActionResult.PASS;
        }
        if (!world.isClient) {
            trap.poison(player.getUuid());
            recordTrapInteraction(player, trap, "poison");
            world.playSound(
                    null,
                    trap.getBlockPos(),
                    SoundEvents.ITEM_BOTTLE_EMPTY,
                    SoundCategory.PLAYERS,
                    0.8F,
                    1.1F
            );
            heldStack.decrement(1);
        }
        return ActionResult.SUCCESS;
    }

    private static HunterTrapEntity nearestTrap(
            World world,
            net.minecraft.util.hit.BlockHitResult hitResult
    ) {
        Box searchBox = new Box(hitResult.getBlockPos()).expand(1.5D);
        return world.getEntitiesByClass(
                        HunterTrapEntity.class,
                        searchBox,
                        trap -> trap.squaredDistanceTo(hitResult.getPos()) < 2.25D
                ).stream()
                .min(Comparator.comparingDouble((HunterTrapEntity trap) -> trap.squaredDistanceTo(hitResult.getPos()))
                        .thenComparingInt(HunterTrapEntity::getId))
                .orElse(null);
    }

    private static void applyDismantleCooldown(PlayerEntity player) {
        Set<Item> cooldownItems = new HashSet<>();
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!stack.isEmpty() && isCooldownBearing(player, stack.getItem())) {
                cooldownItems.add(stack.getItem());
            }
        }

        ItemCooldownManager manager = player.getItemCooldownManager();
        ItemCooldownManagerAccessor managerAccessor = (ItemCooldownManagerAccessor) manager;
        Map<Item, ?> entries = managerAccessor.sparkwitch$getEntries();
        int currentTick = managerAccessor.sparkwitch$getTick();
        for (Item item : cooldownItems) {
            int existingTicks = remainingCooldownTicks(entries.get(item), currentTick);
            if (existingTicks < DISMANTLE_COOLDOWN_TICKS) {
                manager.set(item, DISMANTLE_COOLDOWN_TICKS);
            }
        }
    }

    private static boolean isCooldownBearing(PlayerEntity player, Item item) {
        Identifier itemId = Registries.ITEM.getId(item);
        return GameConstants.ITEM_COOLDOWNS.containsKey(item)
                || item instanceof DoubleBarrelShotgunItem
                || HunterRules.isExtraDismantleCooldownItem(itemId)
                || player.getItemCooldownManager().isCoolingDown(item);
    }

    private static int remainingCooldownTicks(Object entry, int currentTick) {
        if (!(entry instanceof ItemCooldownEntryAccessor accessor)) {
            return 0;
        }
        return Math.max(0, accessor.sparkwitch$getEndTick() - currentTick);
    }

    private static void afterConfirmedDeath(
            ServerPlayerEntity victim,
            ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        GameWorldComponent game = GameWorldComponent.KEY.get(victim.getServerWorld());
        Role victimRole = game.getRole(victim);
        if (victimRole != null && HunterRules.ROLE_ID.equals(victimRole.identifier())) {
            removeHunterWeapons(victim);
        }

        HunterPlayerComponent component = HunterPlayerComponent.KEY.get(victim);
        if (GameConstants.DeathReasons.POISON.equals(deathReason)) {
            PlayerPoisonComponent poison = PlayerPoisonComponent.KEY.get(victim);
            if (component.hasConfirmedTrapPoisonDeath(victim.getServerWorld().getTime(), poison.poisoner)) {
                awardTrapPoison(component.getTrapPoisonAttribution(), victim.getServerWorld());
            }
        }
        component.clearPoisonAttribution();
    }

    private static void removeHunterWeapons(ServerPlayerEntity victim) {
        for (int slot = 0; slot < victim.getInventory().size(); slot++) {
            ItemStack stack = victim.getInventory().getStack(slot);
            if (stack.getItem() instanceof DoubleBarrelShotgunItem
                    || stack.getItem() instanceof DoubleBarrelShellItem) {
                victim.getInventory().setStack(slot, ItemStack.EMPTY);
            }
        }
        victim.currentScreenHandler.sendContentUpdates();
    }

    private static void awardTrapPoison(
            HunterPoisonAttribution attribution,
            ServerWorld world
    ) {
        if (attribution == null) {
            return;
        }
        UUID poisonerUuid = attribution.effectivePoisonerUuid();
        if (poisonerUuid != null) {
            addBalance(world, poisonerUuid, HunterRules.POISONER_REWARD);
        }
        if (attribution.placerUuid() != null) {
            addBalance(world, attribution.placerUuid(), HunterRules.PLACER_REWARD);
        }
    }

    private static void addBalance(ServerWorld world, UUID playerUuid, int amount) {
        PlayerEntity player = world.getPlayerByUuid(playerUuid);
        if (player != null) {
            PlayerShopComponent.KEY.get(player).addToBalance(amount);
        }
    }

    private static ShouldPunishGunShooter.PunishResult gunPunishment(PlayerEntity shooter, PlayerEntity victim) {
        Role role = GameWorldComponent.KEY.get(shooter.getWorld()).getRole(shooter);
        return role != null && HunterRules.ROLE_ID.equals(role.identifier())
                ? ShouldPunishGunShooter.PunishResult.cancel()
                : null;
    }

    private static void cleanupRound(ServerWorld world) {
        for (HunterTrapEntity trap : world.getEntitiesByType(
                TypeFilter.equals(HunterTrapEntity.class),
                trap -> true
        )) {
            trap.discardTrap();
        }
        for (ServerPlayerEntity player : world.getPlayers()) {
            HunterPlayerComponent.KEY.get(player).reset();
        }
    }

    private static void recordTrapInteraction(PlayerEntity player, HunterTrapEntity trap, String action) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) {
            return;
        }
        NbtCompound extra = new NbtCompound();
        extra.putString("action", action);
        GameRecordManager.putBlockPos(extra, "pos", trap.getBlockPos());
        GameRecordManager.recordItemUse(serverPlayer, HunterTrapItem.ID, null, extra);
    }

    private static void registerReplayFormatters() {
        ReplayRegistry.registerItemUseFormatter(HunterTrapItem.ID, (event, match, world) -> {
            NbtCompound data = event.data();
            if (!data.containsUuid("actor")) {
                return null;
            }
            Text actor = ReplayGenerator.formatPlayerName(
                    data.getUuid("actor"),
                    ReplayGenerator.getPlayerInfoCache(match)
            );
            String action = data.getString("action");
            String suffix = switch (action) {
                case "dismantle" -> "dismantle";
                case "pickup" -> "pickup";
                case "poison" -> "poison";
                default -> "place";
            };
            return Text.translatable("replay.item_use.sparkwitch.hunter_trap." + suffix, actor);
        });

        ReplayRegistry.registerItemUseFormatter(DoubleBarrelShotgunItem.ID, (event, match, world) -> {
            NbtCompound data = event.data();
            if (!data.containsUuid("actor")) {
                return null;
            }
            Text actor = ReplayGenerator.formatPlayerName(
                    data.getUuid("actor"),
                    ReplayGenerator.getPlayerInfoCache(match)
            );
            if ("reload".equals(data.getString("action"))) {
                return Text.translatable(
                        "replay.item_use.sparkwitch.double_barrel_shotgun.reload",
                        actor,
                        data.getInt("loaded_shells")
                );
            }
            if (data.containsUuid("target")) {
                Text target = ReplayGenerator.formatPlayerName(
                        data.getUuid("target"),
                        ReplayGenerator.getPlayerInfoCache(match)
                );
                return Text.translatable(
                        "replay.item_use.sparkwitch.double_barrel_shotgun.fire_hit",
                        actor,
                        target
                );
            }
            return Text.translatable("replay.item_use.sparkwitch.double_barrel_shotgun.fire", actor);
        });

        ReplayRegistry.registerGlobalEventFormatter(HunterTrapEntity.EVENT_TRIGGERED, (event, match, world) -> {
            NbtCompound data = event.data();
            if (!data.containsUuid("target")) {
                return null;
            }
            var playerInfo = ReplayGenerator.getPlayerInfoCache(match);
            Text target = ReplayGenerator.formatPlayerName(data.getUuid("target"), playerInfo);
            boolean poisoned = data.getBoolean("poisoned");
            if (!data.containsUuid("actor")) {
                return Text.translatable(
                        poisoned
                                ? "replay.global.sparkwitch.hunter_trap_triggered.poisoned_no_owner"
                                : "replay.global.sparkwitch.hunter_trap_triggered.no_owner",
                        target
                );
            }
            Text owner = ReplayGenerator.formatPlayerName(data.getUuid("actor"), playerInfo);
            if (poisoned && data.containsUuid("poisoner")) {
                Text poisoner = ReplayGenerator.formatPlayerName(data.getUuid("poisoner"), playerInfo);
                return Text.translatable(
                        "replay.global.sparkwitch.hunter_trap_triggered.poisoned",
                        owner,
                        poisoner,
                        target
                );
            }
            return Text.translatable(
                    poisoned
                            ? "replay.global.sparkwitch.hunter_trap_triggered.owner_poisoned"
                            : "replay.global.sparkwitch.hunter_trap_triggered",
                    owner,
                    target
            );
        });
    }
}
