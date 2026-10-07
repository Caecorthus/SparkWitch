package dev.caecorthus.sparkwitch.roles.killer.hunter;

import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.util.hitscan.HitscanLagRules;
import dev.caecorthus.sparkwitch.util.hitscan.PlayerHitboxHistory;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import java.util.List;
import java.util.function.Function;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.StackReference;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ClickType;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/** Hunter's two-shot weapon. Reload mutations share one server-authoritative path. */
public final class DoubleBarrelShotgunItem extends Item {
    public static final Identifier ID = SparkWitch.id("double_barrel_shotgun");
    private static final String LOADED_SHELLS_KEY = "LoadedShells";
    private static final String RELOAD_WINDOW_UNTIL_KEY = "ReloadWindowUntil";
    private static final double TARGET_BOX_EXPANSION = 0.1D;

    public DoubleBarrelShotgunItem(Settings settings) {
        super(settings);
    }

    public static Settings createSettings() {
        return new Settings().maxCount(1);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack shotgun = user.getStackInHand(hand);
        if (SparkTraitsKillerBridge.blocksWeaponAction(user, shotgun)) {
            return TypedActionResult.fail(shotgun);
        }
        if (user.getItemCooldownManager().isCoolingDown(this)) {
            return TypedActionResult.pass(shotgun);
        }

        int loadedShells = getLoadedShells(shotgun);
        if (loadedShells <= 0) {
            if (!world.isClient) {
                world.playSound(
                        null,
                        user.getBlockPos(),
                        SoundEvents.BLOCK_DISPENSER_FAIL,
                        SoundCategory.PLAYERS,
                        0.8F,
                        1.0F
                );
            }
            return TypedActionResult.fail(shotgun);
        }

        if (world.isClient) {
            user.setPitch(user.getPitch() - 4.0F);
            return TypedActionResult.consume(shotgun);
        }

        int remainingShells = loadedShells - 1;
        setLoadedShells(shotgun, remainingShells);
        clearReloadWindow(shotgun);

        // The shooter aimed at its delayed client view of others, so the server tests their rewound volumes.
        // 射手瞄准的是客户端延迟画面中的其他玩家，因此服务端改为检测其回溯后的命中体积。
        PlayerEntity aimed = user instanceof ServerPlayerEntity serverUser
                ? findTarget(user,
                        candidate -> PlayerHitboxHistory.hitVolumes(serverUser, candidate, TARGET_BOX_EXPANSION))
                : findTarget(user);
        // Nearest-wins: a nearer Seeker device absorbs the shot (null target). / 最近者命中：更近的搜寻者设备吸收这一枪。
        PlayerEntity target = SeekerDeviceHits.onShotgunFired(user, aimed, HunterRules.SHOTGUN_RANGE);
        if (user instanceof ServerPlayerEntity shooter && target instanceof ServerPlayerEntity serverTarget) {
            GameFunctions.killPlayer(serverTarget, true, shooter, GameConstants.DeathReasons.GUN);
        }
        if (user instanceof ServerPlayerEntity shooter) {
            NbtCompound extra = new NbtCompound();
            extra.putString("action", "fire");
            extra.putInt("remaining_shells", remainingShells);
            extra.putBoolean("hit", target instanceof ServerPlayerEntity);
            GameRecordManager.recordItemUse(
                    shooter,
                    ID,
                    target instanceof ServerPlayerEntity serverTarget ? serverTarget : null,
                    extra
            );
        }
        world.playSound(
                null,
                user.getBlockPos(),
                SoundEvents.ENTITY_GENERIC_EXPLODE.value(),
                SoundCategory.PLAYERS,
                0.6F,
                1.7F
        );
        user.getItemCooldownManager().set(this, HunterRules.cooldownAfterShot(remainingShells));
        return TypedActionResult.consume(shotgun);
    }

    @Override
    public boolean onClicked(
            ItemStack shotgun,
            ItemStack shells,
            Slot slot,
            ClickType clickType,
            PlayerEntity player,
            StackReference cursorStackReference
    ) {
        if (clickType != ClickType.RIGHT || !tryReload(player, shotgun, shells)) {
            return false;
        }
        if (!player.getWorld().isClient) {
            cursorStackReference.set(shells);
        }
        return true;
    }

    /**
     * The inventory-click and shell-use routes both delegate here so cooldown, timing, and creative rules cannot drift.
     * 背包右键与弹药直接使用统一走这里，避免冷却、装填窗口与创造模式规则分叉。
     */
    public static boolean tryReload(PlayerEntity player, ItemStack shotgun, ItemStack shells) {
        if (SparkTraitsKillerBridge.isKillerInteractionBlocked(player)) {
            return false;
        }
        if (!(shotgun.getItem() instanceof DoubleBarrelShotgunItem shotgunItem)
                || !(shells.getItem() instanceof DoubleBarrelShellItem)) {
            return false;
        }

        int loadedShells = getLoadedShells(shotgun);
        boolean coolingDown = player.getItemCooldownManager().isCoolingDown(shotgunItem);
        long currentTick = player.getWorld().getTime();
        long reloadWindowUntil = getReloadWindowUntil(shotgun);
        if (!HunterRules.canReload(loadedShells, coolingDown, currentTick, reloadWindowUntil)) {
            return false;
        }
        if (player.getWorld().isClient) {
            return true;
        }

        setLoadedShells(shotgun, loadedShells + 1);
        setReloadWindowUntil(
                shotgun,
                HunterRules.reloadWindowAfterLoading(loadedShells, currentTick, reloadWindowUntil)
        );
        if (!player.isCreative()) {
            shells.decrement(1);
        }
        if (player instanceof ServerPlayerEntity serverPlayer) {
            NbtCompound extra = new NbtCompound();
            extra.putString("action", "reload");
            extra.putInt("loaded_shells", loadedShells + 1);
            GameRecordManager.recordItemUse(serverPlayer, ID, null, extra);
        }
        player.getWorld().playSound(
                null,
                player.getBlockPos(),
                SoundEvents.ITEM_ARMOR_EQUIP_CHAIN.value(),
                SoundCategory.PLAYERS,
                0.6F,
                1.4F
        );
        return true;
    }

    public static PlayerEntity findTarget(PlayerEntity user) {
        return findTarget(user, candidate -> List.of(candidate.getBoundingBox().expand(TARGET_BOX_EXPANSION)));
    }

    /**
     * Side-neutral pick: the client crosshair passes current boxes (already the delayed view), the server passes
     * {@link PlayerHitboxHistory} volumes. Eligibility is checked before geometry, so ineligible players stay
     * transparent. / 与端无关的目标选择：客户端准星传入当前箱体（本就是延迟画面），服务端传入
     * {@link PlayerHitboxHistory} 命中体积。先判定资格再算几何，不合格的玩家保持“透明”。
     */
    static PlayerEntity findTarget(PlayerEntity user, Function<PlayerEntity, List<Box>> hitVolumes) {
        Vec3d eye = user.getEyePos();
        Vec3d look = user.getRotationVec(1.0F);
        Vec3d end = eye.add(look.multiply(HunterRules.SHOTGUN_RANGE));
        HitResult blockHit = user.getWorld().raycast(new RaycastContext(
                eye,
                end,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                user
        ));
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getPos();
        }

        PlayerEntity nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (PlayerEntity candidate : user.getWorld().getPlayers()) {
            if (candidate == user
                    || !VendettaInteractionService.isOrdinaryAliveOrBoundKillerTarget(user, candidate)
                    || !GameFunctions.isPlayerAliveAndSurvival(candidate)) {
                continue;
            }
            double distance = HitscanLagRules.entryDistanceSquared(eye, end, hitVolumes.apply(candidate));
            if (distance >= 0.0D && distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable(
                "item.sparkwitch.double_barrel_shotgun.tooltip.line1",
                getLoadedShells(stack)
        ).formatted(Formatting.GRAY));
        for (int line = 2; line <= 5; line++) {
            tooltip.add(Text.translatable(
                    "item.sparkwitch.double_barrel_shotgun.tooltip.line" + line
            ).formatted(Formatting.GRAY));
        }
        super.appendTooltip(stack, context, tooltip, type);
    }

    public static int getLoadedShells(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        return customData == null ? 0 : customData.copyNbt().getInt(LOADED_SHELLS_KEY);
    }

    public static void setLoadedShells(ItemStack stack, int shells) {
        NbtCompound nbt = getOrCreateCustomData(stack);
        nbt.putInt(LOADED_SHELLS_KEY, Math.max(0, Math.min(HunterRules.MAX_SHELLS, shells)));
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    private static long getReloadWindowUntil(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        return customData == null ? 0L : customData.copyNbt().getLong(RELOAD_WINDOW_UNTIL_KEY);
    }

    private static void setReloadWindowUntil(ItemStack stack, long worldTime) {
        NbtCompound nbt = getOrCreateCustomData(stack);
        nbt.putLong(RELOAD_WINDOW_UNTIL_KEY, worldTime);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    private static void clearReloadWindow(ItemStack stack) {
        NbtCompound nbt = getOrCreateCustomData(stack);
        nbt.remove(RELOAD_WINDOW_UNTIL_KEY);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    private static NbtCompound getOrCreateCustomData(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        return customData == null ? new NbtCompound() : customData.copyNbt();
    }
}
