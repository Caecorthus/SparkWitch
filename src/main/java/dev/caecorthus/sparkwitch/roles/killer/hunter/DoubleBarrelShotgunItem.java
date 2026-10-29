package dev.caecorthus.sparkwitch.roles.killer.hunter;

import dev.caecorthus.sparkwitch.compat.SparkStrengthJunctionBoxCompat;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.hit.BlackRavenCrowHits;
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
import java.util.List;
import java.util.Map;
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

        // The shot pops the stack's top shell (last loaded fires first): a slug ('1') fires the old single centre ray
        // at {@link HunterRules#SLUG_RANGE} and kills the nearest victim; a buckshot ('0') fires the 9-ray spread at
        // {@link HunterRules#SHOTGUN_RANGE} with the multi-kill /3 rule (2026-10-29).
        // 射击弹出栈顶弹药（后装先打）：独头弹（'1'）按旧单中心射线判定，射程 {@link HunterRules#SLUG_RANGE}，
        // 击杀最近的 1 名受害者；霰弹（'0'）按 9 条散射射线、射程 {@link HunterRules#SHOTGUN_RANGE} 与 /3 多人击杀规则（2026-10-29）。
        int firedShell = peekShell(shotgun);
        if (firedShell < 0) {
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
        boolean slug = firedShell == 1;

        // One shot = {@link HunterShotgunAim#RAY_COUNT} spread rays; each ray pierces players and collects every
        // eligible one it passes through (client claim confirmed by the server), the interceptors (Raven Form crow,
        // Magician puppet, Seeker device) still absorb exactly as on the old single shot, and the shot kills
        // ceil(hit rays / 3) nearest victims (2026-10-29).
        // 一次射击 = {@link HunterShotgunAim#RAY_COUNT} 条散射射线；每条射线穿透玩家并收集穿过的所有合格玩家
        // （客户端声明由服务端确认），拦截者（化鸦乌鸦、魔术师皮套、搜寻者设备）仍与旧单发完全一样地吸收，
        // 并击杀 ceil(命中射线数 / 3) 名最近的受害者（2026-10-29）。
        List<Aim> aims = slug ? List.of(slugAim(user)) : spreadAims(user);
        List<List<Integer>> claim = user instanceof ServerPlayerEntity serverUser
                ? HunterShotgunAimClaims.take(serverUser) : null;
        List<RayResult> results = new ArrayList<>(aims.size());
        if (user instanceof ServerPlayerEntity serverShooter) {
            for (int index = 0; index < aims.size(); index++) {
                List<Integer> claimed = claim == null || index >= claim.size() ? null : claim.get(index);
                results.add(resolveRay(serverShooter, shotgun, aims.get(index), claimed));
            }
        }

        // Distinct victims ordered by ray entry; a player on several rays still counts every ray for the /3 rule, and
        // one ray through a line of players adds every one of them. / 按射线入射排序的去重受害者；同一玩家在多条射线上时
        // 每条射线都计入 /3 规则，一条射线贯穿一排玩家时每个玩家都加入。
        Map<UUID, RayTarget> byUuid = new LinkedHashMap<>();
        int hitRays = 0;
        for (RayResult result : results) {
            if (!result.victims().isEmpty()) {
                hitRays++;
            }
            for (RayTarget victim : result.victims()) {
                byUuid.merge(victim.player().getUuid(), victim,
                        (first, later) -> first.entryDistanceSquared() <= later.entryDistanceSquared()
                                ? first : later);
            }
        }
        List<RayTarget> victims = byUuid.values().stream()
                .sorted(Comparator.comparingDouble(RayTarget::entryDistanceSquared)).toList();
        // A slug kills exactly the nearest victim on its single ray; buckshot keeps the multi-kill rule.
        // 独头弹只击杀其单射线上最近的 1 名受害者；霰弹保留多人击杀规则。
        int killCount = slug ? Math.min(1, victims.size()) : Math.min((hitRays + 2) / 3, victims.size());
        if (user instanceof ServerPlayerEntity shooter) {
            for (int killed = 0; killed < killCount; killed++) {
                if (victims.get(killed).player() instanceof ServerPlayerEntity serverVictim) {
                    GameFunctions.killPlayer(serverVictim, true, shooter, GameConstants.DeathReasons.GUN);
                }
            }
        }
        // Only the centre ray reaches a wall: players no longer block it (rays pierce them), but the interceptors and
        // an unconfirmed claim still do, like the old single ray (junction box included). The slug's ray uses its own
        // range; the buckshot centre ray uses the spread range.
        // 只有中心射线能打到墙：玩家不再挡住它（射线穿透玩家），但拦截者与未能确认的声明仍会挡住，与旧单发一致（包括接线箱）。
        // 独头弹射线用其自身射程；霰弹中心射线用散射射程。
        if (!results.isEmpty() && results.get(0).reachesWall() && user instanceof ServerPlayerEntity shooter) {
            hitJunctionBox(shooter, slug ? HunterRules.SLUG_RANGE : HunterRules.SHOTGUN_RANGE);
        }
        if (user instanceof ServerPlayerEntity shooter) {
            NbtCompound extra = new NbtCompound();
            extra.putString("action", "fire");
            extra.putInt("remaining_shells", remainingShells);
            extra.putInt("hit_rays", hitRays);
            extra.putInt("victims", victims.size());
            extra.putBoolean("hit", hitRays > 0);
            GameRecordManager.recordItemUse(
                    shooter,
                    ID,
                    hitRays > 0 && victims.get(0).player() instanceof ServerPlayerEntity serverVictim
                            ? serverVictim : null,
                    extra
            );
        }
        world.playSound(
                null,
                user.getBlockPos(),
                SparkWitchSounds.DOUBLE_BARREL_SHOT,
                SoundCategory.PLAYERS,
                1.0F,
                1.0F
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
                || (!(shells.getItem() instanceof DoubleBarrelShellItem)
                && !(shells.getItem() instanceof DoubleBarrelSlugItem))) {
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

        pushShell(shotgun, shells.getItem() instanceof DoubleBarrelSlugItem ? 1 : 0);
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
        return peekShell(shotgun) == 1 ? List.of(slugAim(user)) : spreadAims(user);
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
     * One spread ray's outcome: every player it pierced (each with the squared ray-entry distance for the
     * nearest-victims order), whether an unconfirmed claimed player stopped it (so it reaches no wall), and whether a
     * puppet, device or SparkStrength drone absorbed it. / 一条散射射线的结果：穿过的所有玩家（各含射线入射平方距离，
     * 用于最近者排序）、是否被未能确认的声明玩家截停（因此打不到墙）、是否被皮套、设备或 SparkStrength 无人机吸收。
     */
    private record RayResult(List<RayTarget> victims, boolean blocked, boolean absorbed) {
        static RayResult hit(List<RayTarget> victims) {
            return new RayResult(victims, false, false);
        }

        static RayResult miss(boolean blocked, boolean absorbed) {
            return new RayResult(List.of(), blocked, absorbed);
        }

        boolean reachesWall() {
            return victims.isEmpty() && !blocked && !absorbed;
        }
    }

    /** A ray's pierced players and whether an unconfirmed claim stopped it. / 一条射线穿过的玩家与声明是否被未确认玩家截停。 */
    private record ClaimPick(List<RayTarget> victims, boolean blocked) {
    }

    /**
     * Resolves one spread ray: the ray pierces players and collects every eligible one (the client claim confirmed by
     * the server, as before), then the Raven Form crow, the Magician puppet and the Seeker device steps each compare
     * against the ray's nearest player, exactly as on the old single shot.
     * 解析一条散射射线：射线穿透玩家并收集每个合格玩家（客户端声明由服务端确认，与之前相同），随后化鸦乌鸦、魔术师皮套
     * 与搜寻者设备步骤各自与该射线最近的玩家比较，与旧单发完全一致。
     */
    private static RayResult resolveRay(ServerPlayerEntity user, ItemStack shotgun, Aim aim,
                                        @Nullable List<Integer> claimedIds) {
        ClaimPick pick = pickOnRay(user, aim, claimedIds);
        PlayerEntity nearest = pick.victims().isEmpty() ? null : pick.victims().get(0).player();
        BlackRavenCrowHits.ShotgunHit crow = BlackRavenCrowHits.onShotgunFiredWithEntry(user, nearest,
                aim.eye(), aim.end(), HunterShotgunAim.CONFIRM_BOX_EXPANSION);
        if (crow != null) {
            return RayResult.hit(List.of(new RayTarget(crow.owner(), crow.entryDistanceSquared())));
        }
        if (MagicianPuppetHits.onShotgunFired(user, shotgun, nearest, aim.eye(), aim.end())) {
            return RayResult.miss(false, true);
        }
        // Read-only first: the break below removes the device, and SparkStrength's bomber drones (which absorb inside
        // {@code onShotgunFired} itself) are invisible to it, exactly as on the old centre ray.
        // 先只读询问：下方的打坏会移除设备，且 SparkStrength 轰炸者无人机（在 {@code onShotgunFired} 内部吸收）对它不可见，
        // 与旧中心射线完全一致。
        boolean deviceAhead = SeekerDeviceHits.shotgunPathHasDevice(user, aim.eye(), aim.end());
        PlayerEntity afterDevice = SeekerDeviceHits.onShotgunFired(user, nearest, aim.eye(), aim.end());
        if (afterDevice == null && (nearest != null || deviceAhead)) {
            return RayResult.miss(false, true);
        }
        if (!pick.victims().isEmpty()) {
            return RayResult.hit(pick.victims());
        }
        return RayResult.miss(pick.blocked(), false);
    }

    /**
     * Server pick on one spread ray. The client's per-ray aim claim names who its screen showed on that ray, nearest
     * first; every eligible claimed player whose rewound volumes, grown by
     * {@link HunterShotgunAim#CONFIRM_BOX_EXPANSION}, meet the ray joins the victims in claim order, and the first one
     * that fails confirmation stops the ray (conservative: no pass-through of an unconfirmed player). Without a claim
     * the server's own pick over the rewind window collects every eligible player the ray enters; a Magician puppet
     * proxy never has one — it is a {@link FakePlayer} sharing its owner's UUID, so it must never read (and use up) the
     * owner's claim.
     * 一条散射射线上的服务端选取。客户端对该射线的瞄准声明按由近到远列出其画面中该射线上的玩家；回溯体积
     * （按 {@link HunterShotgunAim#CONFIRM_BOX_EXPANSION} 扩大）与该射线相交的每个合格声明玩家按声明顺序加入受害者，
     * 首个确认失败的玩家截停该射线（保守：不穿过未能确认的玩家）。无声明时服务端自身的回溯选取收集射线进入的所有合格玩家；
     * 魔术师皮套代理永远没有声明——它是与其主人共用 UUID 的 {@link FakePlayer}，因此绝不能读取（并用掉）主人的声明。
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
        List<RayTarget> victims = new ArrayList<>();
        for (int id : claimedIds) {
            if (!(user.getWorld().getEntityById(id) instanceof PlayerEntity player)) {
                continue;
            }
            if (!isShotgunTarget(user, player)) {
                continue;
            }
            double entry = HitscanLagRules.entryDistanceSquared(aim.eye(), aim.end(),
                    PlayerHitboxHistory.hitVolumes(user, player, HunterShotgunAim.CONFIRM_BOX_EXPANSION));
            if (entry < 0.0D) {
                return new ClaimPick(List.copyOf(victims), true);
            }
            victims.add(new RayTarget(player, entry));
        }
        return new ClaimPick(List.copyOf(victims), false);
    }

    /**
     * The shot's spread: {@link HunterShotgunAim#RAY_COUNT} block-clipped rays — the centre, up/down, left/right and
     * the four diagonals — each {@link HunterRules#SHOTGUN_SPREAD_DEGREES} off centre (2026-10-29).
     * 散射：{@link HunterShotgunAim#RAY_COUNT} 条按方块截断的射线——中心、上下、左右与四角——各偏
     * {@link HunterRules#SHOTGUN_SPREAD_DEGREES}（2026-10-29）。
     */
    public static List<Aim> spreadAims(PlayerEntity user) {
        double spread = HunterRules.SHOTGUN_SPREAD_DEGREES;
        float yaw = user.getYaw();
        float pitch = user.getPitch();
        List<Aim> aims = new ArrayList<>(HunterShotgunAim.RAY_COUNT);
        for (double[] offset : SPREAD_OFFSETS) {
            aims.add(aimAlong(user, direction(yaw + offset[0] * spread, pitch + offset[1] * spread),
                    HunterRules.SHOTGUN_RANGE));
        }
        return aims;
    }

    /** Spread offsets in (yaw, pitch) degrees, the centre first. / 以（偏航、俯仰）度数表示的散射偏移，中心在前。 */
    private static final double[][] SPREAD_OFFSETS = {
            {0.0D, 0.0D}, {0.0D, 1.0D}, {0.0D, -1.0D}, {-1.0D, 0.0D}, {1.0D, 0.0D},
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

    /** Eye and block-clipped end of the centre ray. / 中心射线的眼睛位置与按方块截断的终点。 */
    public static Aim aim(PlayerEntity user) {
        return aimAlong(user, user.getRotationVec(1.0F), HunterRules.SHOTGUN_RANGE);
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
     * The shot's centre block ray (COLLIDER shapes, eye to {@link HunterRules#SHOTGUN_RANGE} along the look): the
     * junction-box step hits the block it lands on. / 这一枪的中心方块射线（COLLIDER 形状，沿视线从眼睛到
     * {@link HunterRules#SHOTGUN_RANGE}）：接线箱一步命中其落点方块。
     */
    static HitResult wallHit(PlayerEntity user) {
        return wallHit(user, user.getRotationVec(1.0F), HunterRules.SHOTGUN_RANGE);
    }

    /** {@link #wallHit} along an explicit direction and range, for one spread or slug ray. / 沿显式方向与射程（散射或独头弹射线）的 {@link #wallHit}。 */
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
        tooltip.add(shellTypeText(shells.length() >= 1 ? shells.charAt(shells.length() - 1) - '0' : -1));
        tooltip.add(shellTypeText(shells.length() >= 2 ? shells.charAt(shells.length() - 2) - '0' : -1));
        super.appendTooltip(stack, context, tooltip, type);
    }

    /** Tooltip text of one shell type: 0 = buckshot, 1 = slug, else empty. / 一种弹药的提示文本：0=霰弹，1=独头弹，其余为空。 */
    private static Text shellTypeText(int shell) {
        return Text.translatable(shell == 1
                        ? "item.sparkwitch.double_barrel_shotgun.tooltip.slug"
                        : shell == 0
                        ? "item.sparkwitch.double_barrel_shotgun.tooltip.buckshot"
                        : "item.sparkwitch.double_barrel_shotgun.tooltip.empty")
                .formatted(Formatting.GRAY);
    }

    public static int getLoadedShells(ItemStack stack) {
        return getShellStack(stack).length();
    }

    /**
     * The loaded shells bottom-to-top as chars: '0' = buckshot, '1' = slug (2026-10-29). Items from before the stack
     * era recorded only a count, and every loaded shell then was a buckshot.
     * 自下而上的已装填弹药字符串：'0'=霰弹，'1'=独头弹（2026-10-29）。栈出现之前的旧物品只记录数量，当时每发都是霰弹。
     */
    public static String getShellStack(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return "";
        }
        NbtCompound nbt = customData.copyNbt();
        String shells = nbt.getString(SHELL_STACK_KEY);
        if (!shells.isEmpty()) {
            return shells;
        }
        return "0".repeat(Math.min(nbt.getInt(LOADED_SHELLS_KEY), HunterRules.MAX_SHELLS));
    }

    /** The stack's top shell: 0 = buckshot, 1 = slug, -1 = empty. / 栈顶弹药：0=霰弹，1=独头弹，-1=空。 */
    public static int peekShell(ItemStack stack) {
        String shells = getShellStack(stack);
        return shells.isEmpty() ? -1 : shells.charAt(shells.length() - 1) - '0';
    }

    /** Push one shell (0 or 1): the last loaded fires first. / 压入一发弹药（0 或 1）：后装先打。 */
    static void pushShell(ItemStack stack, int shell) {
        setShellStack(stack, getShellStack(stack) + (shell == 1 ? "1" : "0"));
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
