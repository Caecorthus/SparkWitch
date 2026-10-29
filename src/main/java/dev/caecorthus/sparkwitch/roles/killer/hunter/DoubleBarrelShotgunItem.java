package dev.caecorthus.sparkwitch.roles.killer.hunter;

import dev.caecorthus.sparkwitch.compat.SparkStrengthJunctionBoxCompat;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.hit.BlackRavenCrowHits;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlaybackEntity;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPuppetHits;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.caecorthus.sparkwitch.util.hitscan.HitscanLagRules;
import dev.caecorthus.sparkwitch.util.hitscan.PlayerHitboxHistory;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.fabricmc.fabric.api.entity.FakePlayer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import org.jetbrains.annotations.Nullable;

/** Hunter's two-shot weapon. Reload mutations share one server-authoritative path. */
public final class DoubleBarrelShotgunItem extends Item {
    public static final Identifier ID = SparkWitch.id("double_barrel_shotgun");
    private static final String SHELL_STACK_KEY = "ShellStack";
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

        // The shot pops the stack's top shell (last loaded fires first); see fire() for what each shell does.
        // 射击弹出栈顶弹药（后装先打）；每种弹药的效果见 fire()。
        int firedShell = peekShell(shotgun);
        if (firedShell == HunterRules.SHELL_NONE) {
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

        popShell(shotgun);
        clearReloadWindow(shotgun);
        int remainingShells = getLoadedShells(shotgun);
        if (user instanceof ServerPlayerEntity shooter) {
            fire(shooter, shotgun, firedShell == HunterRules.SHELL_SLUG, remainingShells);
        }
        // From the muzzle (eye), not the block centre: the client spatializes this stereo sound
        // (DoubleBarrelShotAudio), so the shooter hears it centred and listeners get the true direction.
        // 从枪口（眼睛）而非方块中心播放：客户端会对这段立体声做空间化（DoubleBarrelShotAudio），射手因此听到居中的枪声，
        // 其他人听到真实方向。
        world.playSound(
                null,
                user.getX(),
                user.getEyeY(),
                user.getZ(),
                SparkWitchSounds.DOUBLE_BARREL_SHOT,
                SoundCategory.PLAYERS,
                1.0F,
                1.0F
        );
        user.getItemCooldownManager().set(this, HunterRules.cooldownAfterShot(remainingShells));
        return TypedActionResult.consume(shotgun);
    }

    /**
     * Resolves one shot on the server (2026-10-29). A slug fires the single centre ray at
     * {@link HunterRules#SLUG_RANGE} and kills its first victim, the old shotgun shot. Buckshot fires
     * {@link HunterShotgunAim#RAY_COUNT} pellet rays at {@link HunterRules#SHOTGUN_RANGE} that pierce players and kill
     * {@link HunterRules#buckshotKillCount} of the nearest victims. Three phases:
     * <ol>
     * <li>Read-only, every ray: the pierced players (the client claim confirmed by the server), then the nearest-wins
     * interceptors against that ray's nearest player in the old single-shot order (Raven Form crow, Magician puppet,
     * Seeker device). Nothing is ended or broken yet, so a puppet or device that one pellet takes still absorbs every
     * other pellet that reached it first.</li>
     * <li>SparkStrength seam: the frozen {@link SeekerDeviceHits#onShotgunFired(PlayerEntity, PlayerEntity, double)}
     * hook, once, for the centre ray, skipped when a crow or puppet took that ray (as before). Its Bomber drone and
     * Partygoer steps measure only the shooter's look and promise "the caller hits nobody", so a drone or Partygoer
     * that takes the centre ray absorbs the whole shot.</li>
     * <li>Writes: each absorbing puppet ends once, each absorbing device breaks once, then the kills, the junction box
     * (centre ray only) and the record.</li>
     * </ol>
     * 服务端结算一枪（2026-10-29）。独头弹沿单条中心射线射出，射程 {@link HunterRules#SLUG_RANGE}，击杀其第一名受害者，即旧猎枪
     * 单发。霰弹射出 {@link HunterShotgunAim#RAY_COUNT} 条弹丸射线，射程 {@link HunterRules#SHOTGUN_RANGE}，穿透玩家，并击杀
     * {@link HunterRules#buckshotKillCount} 名最近的受害者。分三阶段：
     * <ol>
     * <li>只读，逐条射线：穿过的玩家（客户端声明由服务端确认），随后按旧单发顺序（化鸦乌鸦、魔术师皮套、搜寻者设备）与该射线最近的
     * 玩家做最近者比较。此时不结束、不打坏任何东西，因此被某颗弹丸命中的皮套或设备仍会吸收其他所有先到达它的弹丸。</li>
     * <li>SparkStrength 接缝：冻结的 {@link SeekerDeviceHits#onShotgunFired(PlayerEntity, PlayerEntity, double)} 钩子，只为中心
     * 射线调用一次；乌鸦或皮套取走中心射线时跳过（与之前一致）。其轰炸者无人机与派对客步骤只沿射手视线测量，并承诺“调用方不再命中
     * 任何人”，因此取走中心射线的无人机或派对客吸收整枪。</li>
     * <li>写入：每个吸收弹丸的皮套只结束一次、每个设备只打坏一次，然后击杀、接线箱（仅中心射线）与记录。</li>
     * </ol>
     */
    private static void fire(ServerPlayerEntity shooter, ItemStack shotgun, boolean slug, int remainingShells) {
        double range = slug ? HunterRules.SLUG_RANGE : HunterRules.SHOTGUN_RANGE;
        List<Aim> aims = slug ? List.of(slugAim(shooter)) : spreadAims(shooter);
        // A Magician puppet proxy is a FakePlayer sharing its owner's UUID: it must never read (and use up) the
        // owner's claim, so it always takes the server's own pick.
        // 魔术师皮套代理是与主人共用 UUID 的 FakePlayer：绝不能读取（并用掉）主人的声明，因此始终使用服务端自行选取。
        List<List<Integer>> claim = shooter instanceof FakePlayer ? null : HunterShotgunAimClaims.take(shooter);
        List<RayOutcome> rays = new ArrayList<>(aims.size());
        for (int index = 0; index < aims.size(); index++) {
            rays.add(readRay(shooter, aims.get(index), HunterShotgunAim.claimForRay(claim, aims.size(), index)));
        }

        RayOutcome centre = rays.get(0);
        boolean sparkStrengthAbsorbed = false;
        if (centre.crow() == null && centre.puppet() == null) {
            PlayerEntity aimed = centre.nearest();
            if (SeekerDeviceHits.onShotgunFired(shooter, aimed, range) == null) {
                // Null means something nearer than the aimed player took the centre ray: the Seeker device this ray
                // already found (the hook has now broken it), else a SparkStrength drone or Partygoer. With no aimed
                // player only a device that survived the call tells SparkStrength apart; a drone or Partygoer on an
                // empty centre ray absorbs just that ray, as on the old miss.
                // 返回 null 表示有比被瞄准玩家更近的东西取走了中心射线：本射线已找到的搜寻者设备（钩子已将其打坏），否则是
                // SparkStrength 无人机或派对客。没有被瞄准玩家时，只有调用后仍存在的设备能区分出 SparkStrength；空中心射线上的
                // 无人机或派对客只吸收该射线，与旧的未命中一致。
                sparkStrengthAbsorbed = centre.device() != null ? !centre.device().isRemoved() : aimed != null;
            }
        }

        // Distinct victims ordered by ray entry; a player on several rays counts every ray for the kill count, and one
        // ray through a line of players adds each of them.
        // 按射线入射排序的去重受害者；同一玩家出现在多条射线上时每条射线都计入击杀人数，一条射线贯穿一排玩家时每人都加入。
        Set<MagicianPlaybackEntity> puppets = new LinkedHashSet<>();
        Set<SeekerDeviceEntity> devices = new LinkedHashSet<>();
        Map<UUID, RayTarget> byUuid = new LinkedHashMap<>();
        int hitRays = 0;
        if (!sparkStrengthAbsorbed) {
            for (RayOutcome ray : rays) {
                if (ray.puppet() != null) {
                    puppets.add(ray.puppet());
                } else if (ray.device() != null) {
                    devices.add(ray.device());
                }
                List<RayTarget> rayVictims = ray.victims();
                if (!rayVictims.isEmpty()) {
                    hitRays++;
                }
                for (RayTarget victim : rayVictims) {
                    byUuid.merge(victim.player().getUuid(), victim,
                            (first, later) -> first.entryDistanceSquared() <= later.entryDistanceSquared()
                                    ? first : later);
                }
            }
        }
        for (MagicianPlaybackEntity puppet : puppets) {
            MagicianPuppetHits.endShotgunPuppet(shooter, shotgun, puppet);
        }
        for (SeekerDeviceEntity device : devices) {
            SeekerDeviceHits.breakByShotgun(shooter, device);
        }

        List<RayTarget> victims = byUuid.values().stream()
                .sorted(Comparator.comparingDouble(RayTarget::entryDistanceSquared)).toList();
        int killCount = slug ? Math.min(1, victims.size()) : HunterRules.buckshotKillCount(hitRays, victims.size());
        ServerPlayerEntity firstKilled = null;
        for (int killed = 0; killed < killCount; killed++) {
            if (victims.get(killed).player() instanceof ServerPlayerEntity victim) {
                if (firstKilled == null) {
                    firstKilled = victim;
                }
                GameFunctions.killPlayer(victim, true, shooter, GameConstants.DeathReasons.GUN);
            }
        }
        // Only the centre ray reaches a wall (junction box), and only when nothing took it, as on the old single ray: a
        // player it hit, an interceptor or an unconfirmed claimed player keeps it off the box even though pellets
        // pierce players.
        // 只有中心射线能打到墙（接线箱），且仅当没有任何东西取走它时，与旧单发一致：它命中的玩家、拦截者或未能确认的声明玩家
        // 都会使其打不到箱子，即使弹丸会穿透玩家。
        if (!sparkStrengthAbsorbed && centre.reachesWall()) {
            hitJunctionBox(shooter, range);
        }
        NbtCompound extra = new NbtCompound();
        extra.putString("action", "fire");
        extra.putInt("remaining_shells", remainingShells);
        extra.putInt("hit_rays", hitRays);
        extra.putInt("victims", victims.size());
        extra.putBoolean("hit", hitRays > 0);
        GameRecordManager.recordItemUse(shooter, ID, firstKilled, extra);
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
                || !(shells.getItem() instanceof DoubleBarrelAmmoItem ammo)) {
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

        pushShell(shotgun, ammo.shellType());
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
                SparkWitchSounds.DOUBLE_BARREL_LOAD,
                SoundCategory.PLAYERS,
                0.6F,
                1.0F
        );
        return true;
    }

    /**
     * Side-neutral crosshair pick over the next shot's rays — the slug's single centre ray or the whole spread: the
     * nearest eligible player any ray enters (current boxes on the client — already the delayed view —, rewound
     * volumes on the server). Eligibility is checked before geometry, so ineligible players stay transparent.
     * 端无关的下一发射线选取——独头弹的单中心射线或整片散射：任意射线进入的最近合格玩家（客户端传入当前箱体，
     * 本就是延迟画面；服务端传入回溯体积）。先判定资格再算几何，不合格的玩家保持“透明”。
     */
    public static PlayerEntity findTarget(PlayerEntity user, ItemStack shotgun) {
        return findTarget(user, shotgun,
                candidate -> List.of(candidate.getBoundingBox().expand(TARGET_BOX_EXPANSION)));
    }

    /** {@link #findTarget} with explicit hit volumes. / 显式指定命中体积的 {@link #findTarget}。 */
    static PlayerEntity findTarget(PlayerEntity user, ItemStack shotgun, Function<PlayerEntity, List<Box>> hitVolumes) {
        PlayerEntity nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (Aim aim : shotAims(user, shotgun)) {
            RayTarget rayTarget = findTargetOnRay(user, aim, hitVolumes);
            if (rayTarget.player() != null && rayTarget.entryDistanceSquared() < nearestDistance) {
                nearest = rayTarget.player();
                nearestDistance = rayTarget.entryDistanceSquared();
            }
        }
        return nearest;
    }

    /**
     * Aims of the next shot by the stack's top shell: a slug fires the single centre ray at its range, a buckshot
     * the 9-ray spread (an empty gun still reports the spread; the shot itself is refused upstream).
     * 按栈顶弹药返回下一发射线：独头弹为其射程的单中心射线，霰弹为 9 条散射射线（空枪仍返回散射；射击本身在上游被拒绝）。
     */
    public static List<Aim> shotAims(PlayerEntity user, ItemStack shotgun) {
        return peekShell(shotgun) == HunterRules.SHELL_SLUG ? List.of(slugAim(user)) : spreadAims(user);
    }

    /** A player one ray picks, and the squared ray-entry distance. / 一条射线选取的玩家及其射线入射平方距离。 */
    private record RayTarget(@Nullable PlayerEntity player, double entryDistanceSquared) {
    }

    /** Nearest eligible pick on one ray. / 一条射线上的最近合格选取。 */
    private static RayTarget findTargetOnRay(PlayerEntity user, Aim aim,
                                             Function<PlayerEntity, List<Box>> hitVolumes) {
        PlayerEntity nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (PlayerEntity candidate : user.getWorld().getPlayers()) {
            if (!isShotgunTarget(user, candidate)) {
                continue;
            }
            double distance = HitscanLagRules.entryDistanceSquared(aim.eye(), aim.end(), hitVolumes.apply(candidate));
            if (distance >= 0.0D && distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return new RayTarget(nearest, nearestDistance);
    }

    /**
     * One pellet ray, read before anything is written: the players it pierced (nearest first, each with its squared
     * ray entry), whether an unconfirmed claimed player stopped it, and the interceptor that took it, if any (at most
     * one of crow, puppet, device).
     * 一条弹丸射线在任何写入之前的读取结果：它穿过的玩家（由近到远，各含射线入射平方距离）、是否被未能确认的声明玩家截停，以及
     * 取走它的拦截者（乌鸦、皮套、设备至多其一）。
     */
    private record RayOutcome(List<RayTarget> pierced, boolean stopped, @Nullable RayTarget crow,
                              @Nullable MagicianPlaybackEntity puppet, @Nullable SeekerDeviceEntity device) {
        @Nullable
        PlayerEntity nearest() {
            return pierced.isEmpty() ? null : pierced.get(0).player();
        }

        /** The crow's owner, nobody behind a puppet or device, else every pierced player. / 本射线的受害者。 */
        List<RayTarget> victims() {
            if (crow != null) {
                return List.of(crow);
            }
            return puppet != null || device != null ? List.of() : pierced;
        }

        boolean reachesWall() {
            return crow == null && puppet == null && device == null && pierced.isEmpty() && !stopped;
        }
    }

    /** A ray's pierced players and whether an unconfirmed claim stopped it. / 一条射线穿过的玩家与声明是否被未确认玩家截停。 */
    private record ClaimPick(List<RayTarget> victims, boolean stopped) {
    }

    /**
     * Reads one pellet ray: the pierced players (the client claim confirmed by the server), then the Raven Form crow,
     * the Magician puppet and the Seeker device, each against the ray's nearest player in the old single-shot order.
     * All three queries are read-only; {@link #fire} writes afterwards.
     * 读取一条弹丸射线：穿过的玩家（客户端声明由服务端确认），随后按旧单发顺序依次以该射线最近的玩家比较化鸦乌鸦、魔术师皮套与
     * 搜寻者设备。三项查询均为只读；由 {@link #fire} 随后写入。
     */
    private static RayOutcome readRay(ServerPlayerEntity user, Aim aim, @Nullable List<Integer> claimedIds) {
        ClaimPick pick = pickOnRay(user, aim, claimedIds);
        PlayerEntity nearest = pick.victims().isEmpty() ? null : pick.victims().get(0).player();
        BlackRavenCrowHits.ShotgunHit crow = BlackRavenCrowHits.onShotgunFired(user, nearest, aim.eye(), aim.end(),
                HunterShotgunAim.CONFIRM_BOX_EXPANSION);
        if (crow != null) {
            return new RayOutcome(pick.victims(), pick.stopped(),
                    new RayTarget(crow.owner(), crow.entryDistanceSquared()), null, null);
        }
        MagicianPlaybackEntity puppet = MagicianPuppetHits.shotgunPuppetOnRay(user, nearest, aim.eye(), aim.end());
        if (puppet != null) {
            return new RayOutcome(pick.victims(), pick.stopped(), null, puppet, null);
        }
        return new RayOutcome(pick.victims(), pick.stopped(), null, null,
                SeekerDeviceHits.shotgunDeviceOnRay(user, nearest, aim.eye(), aim.end()));
    }

    /**
     * Server pick on one pellet ray. The client's per-ray aim claim names who its screen showed on that ray, nearest
     * first; {@link HunterShotgunAim#confirmAll} keeps every eligible claimed player whose rewound volumes, grown by
     * {@link HunterShotgunAim#CONFIRM_BOX_EXPANSION}, meet the ray, and the first one that fails stops the ray (no
     * pass-through of an unconfirmed player). Without a claim the server's own pick over the rewind window collects
     * every eligible player the ray enters; a Magician puppet proxy never reads one (see {@link #fire}).
     * 一条弹丸射线上的服务端选取。客户端对该射线的瞄准声明按由近到远列出其画面中该射线上的玩家；
     * {@link HunterShotgunAim#confirmAll} 保留回溯体积（按 {@link HunterShotgunAim#CONFIRM_BOX_EXPANSION} 扩大）与该射线相交的
     * 每个合格声明玩家，首个确认失败者截停该射线（不穿过未能确认的玩家）。无声明时服务端自身的回溯选取收集射线进入的所有合格玩家；
     * 魔术师皮套代理从不读取声明（见 {@link #fire}）。
     */
    private static ClaimPick pickOnRay(ServerPlayerEntity user, Aim aim, @Nullable List<Integer> claimedIds) {
        if (claimedIds == null) {
            List<RayTarget> victims = new ArrayList<>();
            for (PlayerEntity candidate : user.getWorld().getPlayers()) {
                if (!isShotgunTarget(user, candidate)) {
                    continue;
                }
                double entry = HitscanLagRules.entryDistanceSquared(aim.eye(), aim.end(),
                        PlayerHitboxHistory.hitVolumes(user, candidate, TARGET_BOX_EXPANSION));
                if (entry >= 0.0D) {
                    victims.add(new RayTarget(candidate, entry));
                }
            }
            victims.sort(Comparator.comparingDouble(RayTarget::entryDistanceSquared));
            return new ClaimPick(List.copyOf(victims), false);
        }
        List<PlayerEntity> claimed = new ArrayList<>(claimedIds.size());
        for (int id : claimedIds) {
            if (user.getWorld().getEntityById(id) instanceof PlayerEntity player) {
                claimed.add(player);
            }
        }
        HunterShotgunAim.Confirmation<PlayerEntity> confirmation = HunterShotgunAim.confirmAll(claimed,
                candidate -> isShotgunTarget(user, candidate),
                candidate -> HitscanLagRules.entryDistanceSquared(aim.eye(), aim.end(),
                        PlayerHitboxHistory.hitVolumes(user, candidate, HunterShotgunAim.CONFIRM_BOX_EXPANSION)));
        return new ClaimPick(confirmation.hits().stream()
                .map(hit -> new RayTarget(hit.target(), hit.entrySquared()))
                .toList(), confirmation.stopped());
    }

    /**
     * The buckshot's pellet rays (2026-10-29): {@link HunterShotgunAim#RAY_COUNT} block-clipped rays — the centre
     * first, then up/down, left/right and the four diagonals, each {@link HunterRules#SHOTGUN_SPREAD_DEGREES} off the
     * centre in yaw/pitch. The centre is the vanilla look vector, the same ray the SparkStrength hook and the junction
     * box measure.
     * 霰弹的弹丸射线（2026-10-29）：{@link HunterShotgunAim#RAY_COUNT} 条按方块截断的射线——中心在前，然后是上下、左右与四角，
     * 各在偏航/俯仰上偏离中心 {@link HunterRules#SHOTGUN_SPREAD_DEGREES}。中心即原版视线向量，与 SparkStrength 钩子和接线箱
     * 测量的是同一条射线。
     */
    public static List<Aim> spreadAims(PlayerEntity user) {
        double spread = HunterRules.SHOTGUN_SPREAD_DEGREES;
        float yaw = user.getYaw();
        float pitch = user.getPitch();
        List<Aim> aims = new ArrayList<>(HunterShotgunAim.RAY_COUNT);
        aims.add(aimAlong(user, user.getRotationVec(1.0F), HunterRules.SHOTGUN_RANGE));
        for (double[] offset : SPREAD_OFFSETS) {
            aims.add(aimAlong(user, direction(yaw + offset[0] * spread, pitch + offset[1] * spread),
                    HunterRules.SHOTGUN_RANGE));
        }
        return aims;
    }

    /** Off-centre pellet offsets in (yaw, pitch) spread units. / 偏离中心的弹丸偏移，以（偏航、俯仰）散射单位表示。 */
    private static final double[][] SPREAD_OFFSETS = {
            {0.0D, 1.0D}, {0.0D, -1.0D}, {-1.0D, 0.0D}, {1.0D, 0.0D},
            {-1.0D, 1.0D}, {-1.0D, -1.0D}, {1.0D, 1.0D}, {1.0D, -1.0D}
    };

    /** Vanilla look direction for explicit yaw and pitch, in degrees. / 以度数表示偏航与俯仰时的原版视线方向。 */
    private static Vec3d direction(double yawDegrees, double pitchDegrees) {
        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        double cosPitch = Math.cos(pitch);
        return new Vec3d(-Math.sin(yaw) * cosPitch, -Math.sin(pitch), Math.cos(yaw) * cosPitch);
    }

    /** Eye and block-clipped end of one ray along {@code dir}. / 沿 {@code dir} 的一条射线的眼睛位置与按方块截断的终点。 */
    private static Aim aimAlong(PlayerEntity user, Vec3d dir, double range) {
        Vec3d eye = user.getEyePos();
        HitResult blockHit = wallHit(user, dir, range);
        return new Aim(eye, blockHit.getType() == HitResult.Type.MISS
                ? eye.add(dir.multiply(range))
                : blockHit.getPos());
    }

    /** The slug's centre ray at {@link HunterRules#SLUG_RANGE}. / 射程为 {@link HunterRules#SLUG_RANGE} 的独头弹中心射线。 */
    public static Aim slugAim(PlayerEntity user) {
        return aimAlong(user, user.getRotationVec(1.0F), HunterRules.SLUG_RANGE);
    }

    /** Shared by the server pick, the claim check and the client claim. / 服务端选取、声明确认与客户端声明共用。 */
    public record Aim(Vec3d eye, Vec3d end) {
    }

    private static boolean isShotgunTarget(PlayerEntity user, PlayerEntity candidate) {
        return candidate != user
                && VendettaInteractionService.isOrdinaryAliveOrBoundKillerTarget(user, candidate)
                && GameFunctions.isPlayerAliveAndSurvival(candidate);
    }

    /**
     * One shot ray's block hit (COLLIDER shapes, eye to {@code range} along {@code dir}): {@link #aimAlong} clips the
     * player pick and the client claim there, and the junction-box step hits the block the centre ray lands on.
     * 一条射线的方块命中（COLLIDER 形状，沿 {@code dir} 从眼睛到 {@code range}）：{@link #aimAlong} 在此截断玩家选取与客户端
     * 声明，接线箱一步命中中心射线的落点方块。
     */
    private static HitResult wallHit(PlayerEntity user, Vec3d dir, double range) {
        Vec3d eye = user.getEyePos();
        return user.getWorld().raycast(new RaycastContext(
                eye,
                eye.add(dir.multiply(range)),
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
    private static void hitJunctionBox(ServerPlayerEntity shooter, double range) {
        if (wallHit(shooter, shooter.getRotationVec(1.0F), range) instanceof BlockHitResult wall
                && wall.getType() == HitResult.Type.BLOCK
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
        // The last two lines show the shell stack: the top (fires next) then the shell below it, like a real stack.
        // 最后两行展示弹药栈：栈顶（下一发）在上、其下的一发在后，与真实栈一致。
        String shells = getShellStack(stack);
        tooltip.add(shellTypeText(HunterRules.topShell(shells)));
        tooltip.add(shellTypeText(HunterRules.topShell(shells.substring(0, Math.max(0, shells.length() - 1)))));
        super.appendTooltip(stack, context, tooltip, type);
    }

    /** Tooltip text of one shell type. / 一种弹药的提示文本。 */
    private static Text shellTypeText(int shell) {
        return Text.translatable(shell == HunterRules.SHELL_SLUG
                        ? "item.sparkwitch.double_barrel_shotgun.tooltip.slug"
                        : shell == HunterRules.SHELL_BUCKSHOT
                        ? "item.sparkwitch.double_barrel_shotgun.tooltip.buckshot"
                        : "item.sparkwitch.double_barrel_shotgun.tooltip.empty")
                .formatted(Formatting.GRAY);
    }

    public static int getLoadedShells(ItemStack stack) {
        return getShellStack(stack).length();
    }

    /**
     * The loaded shells bottom-to-top as digit chars of {@link HunterRules#SHELL_BUCKSHOT} /
     * {@link HunterRules#SHELL_SLUG} (custom data {@code ShellStack}, 2026-10-29), cleaned by
     * {@link HunterRules#sanitizeShellStack}. {@code LoadedShells} keeps mirroring the count; an item from before the
     * stack recorded only that count, and every shell then was a buckshot.
     * 自下而上的已装填弹药，以 {@link HunterRules#SHELL_BUCKSHOT} / {@link HunterRules#SHELL_SLUG} 的数字字符表示（自定义数据
     * {@code ShellStack}，2026-10-29），经 {@link HunterRules#sanitizeShellStack} 清理。{@code LoadedShells} 仍同步记录数量；
     * 弹药栈之前的旧物品只记录该数量，当时每发都是霰弹。
     */
    public static String getShellStack(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return "";
        }
        NbtCompound nbt = customData.copyNbt();
        if (nbt.contains(SHELL_STACK_KEY)) {
            return HunterRules.sanitizeShellStack(nbt.getString(SHELL_STACK_KEY));
        }
        return String.valueOf(HunterRules.SHELL_BUCKSHOT)
                .repeat(Math.max(0, Math.min(nbt.getInt(LOADED_SHELLS_KEY), HunterRules.MAX_SHELLS)));
    }

    /** The stack's top shell (fires next), or {@link HunterRules#SHELL_NONE}. / 栈顶弹药（下一发），空时为 NONE。 */
    public static int peekShell(ItemStack stack) {
        return HunterRules.topShell(getShellStack(stack));
    }

    /** Push one shell: the last loaded fires first. / 压入一发弹药：后装先打。 */
    static void pushShell(ItemStack stack, int shell) {
        if (shell != HunterRules.SHELL_BUCKSHOT && shell != HunterRules.SHELL_SLUG) {
            return;
        }
        setShellStack(stack, HunterRules.sanitizeShellStack(getShellStack(stack) + (char) ('0' + shell)));
    }

    /** Pop the top shell. / 弹出栈顶弹药。 */
    private static void popShell(ItemStack stack) {
        String shells = getShellStack(stack);
        if (!shells.isEmpty()) {
            setShellStack(stack, shells.substring(0, shells.length() - 1));
        }
    }

    private static void setShellStack(ItemStack stack, String shells) {
        NbtCompound nbt = getOrCreateCustomData(stack);
        nbt.putString(SHELL_STACK_KEY, shells);
        nbt.putInt(LOADED_SHELLS_KEY, shells.length());
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
