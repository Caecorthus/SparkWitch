package dev.caecorthus.sparkwitch.roles.killer.hunter;

import dev.caecorthus.sparkwitch.compat.SparkStrengthJunctionBoxCompat;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPuppetHits;
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
import net.minecraft.registry.Registries;
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
import net.minecraft.util.hit.BlockHitResult;
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
        // Nearest-wins: a nearer Magician puppet, else a nearer Seeker device, absorbs the shot (null target).
        // 最近者命中：更近的魔术师皮套（其次是更近的搜寻者设备）吸收这一枪。
        boolean puppetAbsorbed = MagicianPuppetHits.onShotgunFired(user, shotgun, aimed, HunterRules.SHOTGUN_RANGE);
        // The ray already stops at the first block, so the shot reaches that block only when no eligible player, puppet
        // or device took it. Asked before the device step, which breaks the device. A SparkStrength Bomber drone
        // absorbs inside that step without a signal, so one between the Hunter and a junction box lets this shot hit
        // the box too (accepted, like the puppet step's drone case).
        // 射线本就止于第一个方块，因此只有合格玩家、皮套与设备都未吸收时这一枪才打到该方块。须在设备一步之前询问，因其会打坏
        // 设备。SparkStrength 轰炸者无人机在那一步内部吸收且不给出信号，所以猎人与接线箱之间的无人机会让这一枪同时打中箱子
        // （已接受，与皮套一步的无人机情形相同）。
        boolean reachesWall = aimed == null && !puppetAbsorbed
                && !SeekerDeviceHits.shotgunPathHasDevice(user, HunterRules.SHOTGUN_RANGE);
        PlayerEntity target = puppetAbsorbed
                ? null : SeekerDeviceHits.onShotgunFired(user, aimed, HunterRules.SHOTGUN_RANGE);
        if (user instanceof ServerPlayerEntity shooter && target instanceof ServerPlayerEntity serverTarget) {
            GameFunctions.killPlayer(serverTarget, true, shooter, GameConstants.DeathReasons.GUN);
        }
        if (reachesWall && user instanceof ServerPlayerEntity shooter) {
            hitJunctionBox(shooter);
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
        Vec3d end = eye.add(user.getRotationVec(1.0F).multiply(HunterRules.SHOTGUN_RANGE));
        HitResult blockHit = wallHit(user);
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

    /**
     * The shot's block ray (COLLIDER shapes, eye to {@link HunterRules#SHOTGUN_RANGE} along the look): the player pick
     * stops there, and the junction-box step hits the block it lands on. / 这一枪的方块射线（COLLIDER 形状，沿视线从眼睛到
     * {@link HunterRules#SHOTGUN_RANGE}）：玩家选取止于此处，接线箱一步命中其落点方块。
     */
    static HitResult wallHit(PlayerEntity user) {
        Vec3d eye = user.getEyePos();
        return user.getWorld().raycast(new RaycastContext(
                eye,
                eye.add(user.getRotationVec(1.0F).multiply(HunterRules.SHOTGUN_RANGE)),
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                user
        ));
    }

    /**
     * A shot that reached the wall damages the SparkStrength junction box it landed on (owner decision 2026-10-21).
     * The id filter keeps reflection off every other block; SparkStrength decides the rest silently.
     * 打到墙上的一枪会伤害其落点处的 SparkStrength 接线箱（所有者 2026-10-21 决定）。先按 id 过滤，其他方块不触发反射；
     * 其余条件由 SparkStrength 静默判定。
     */
    private static void hitJunctionBox(ServerPlayerEntity shooter) {
        if (wallHit(shooter) instanceof BlockHitResult wall && wall.getType() == HitResult.Type.BLOCK
                && SparkStrengthJunctionBoxCompat.JUNCTION_BOX_ID.equals(
                        Registries.BLOCK.getId(shooter.getWorld().getBlockState(wall.getBlockPos()).getBlock()))) {
            SparkStrengthJunctionBoxCompat.damage(shooter, wall.getBlockPos(), HunterRules.SHOTGUN_JUNCTION_BOX_DAMAGE);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable(
                "item.sparkwitch.double_barrel_shotgun.tooltip.line1",
                getLoadedShells(stack)
        ).formatted(Formatting.GRAY));
        for (int line = 2; line <= 6; line++) {
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
