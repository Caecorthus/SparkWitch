package dev.caecorthus.sparkwitch.item.m1903a4;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.caecorthus.sparkwitch.compat.SparkTraitsGunBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.roles.civilian.bear.ricochet.RangedSmokeCarve;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertTaserTargeting;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteSessionService;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.hit.BlackRavenCrowHits;
import dev.caecorthus.sparkwitch.roles.killer.dominator.DominatorForcedShotGuns;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperControlComponent;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlaybackEntity;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPuppetHits;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.caecorthus.sparkwitch.util.OffMatchUse;
import dev.caecorthus.sparkwitch.util.RoundParticipants;
import dev.caecorthus.sparkwitch.util.hitscan.HitscanLagRules;
import dev.caecorthus.sparkwitch.util.hitscan.PlayerHitboxHistory;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * M1903A4 fire gate and server hitscan, following the Sphinx Hao "手"枪. The shot is server-authoritative along the
 * shooter's current server rotation (no aim payload: vanilla has applied the latest movement packet's rotation, which
 * can trail the client's release by about a tick): the eye ray of {@link #RANGE} cut at the first collider block,
 * nearest eligible player against lag-compensated volumes, then nearest wins against Raven Form crows, Magician
 * puppets and the Seeker / SparkStrength seam ({@link #resolve}). A player hit (a crow's owner included) is ONE
 * non-forced {@code killPlayer} with Wathe's {@code GUN} reason, so every shield, veto, BEAR ricochet, Bodyguard vest,
 * Knight duel rule and SparkTraits interception applies; only a real terminal death plays the kill confirmation, to
 * the shooter alone.
 * M1903A4 的开火门槛与服务端即时射线，沿用狮身豪“手”枪。射击沿射手当前的服务端朝向进行，为服务端权威（没有瞄准数据包：
 * 原版已应用最近一个移动数据包的朝向，可能比客户端松手晚约一刻）：长 {@link #RANGE}、在第一个碰撞方块处截断的眼睛射线，
 * 对延迟补偿后的体积选取最近的合格玩家，然后与化鸦乌鸦、魔术师皮套及搜寻者 / SparkStrength 接缝比较最近者
 * （{@link #resolve}）。命中玩家（含乌鸦的主人）即一次以 Wathe {@code GUN} 死因进行的非强制 {@code killPlayer}，因此所有
 * 护盾、否决、BEAR 跳弹、保镖防弹衣、骑士决斗规则与 SparkTraits 拦截都生效；只有真正的最终死亡才会播放击杀确认音效，且只给
 * 射手本人。
 */
public final class M1903A4Combat {
    /** Gun range and crosshair range, both 50 blocks. / 射程与准星判定距离同为 50 格。 */
    public static final double RANGE = 50.0D;
    private static final double SMOKE_STEP = 1.0D;

    private M1903A4Combat() {
    }

    /**
     * The fire gate, asked at charge start ({@code use}) and again at release ({@code onStoppedUsing}). The server
     * decides with every fact; the client only predicts with what it sees (main hand, spectator, cooldown, rounds), so
     * a client that charges a refused rifle simply never fires.
     * 开火门槛，在开始蓄力（{@code use}）与松开（{@code onStoppedUsing}）时各询问一次。服务端用全部事实裁决；客户端只用自己
     * 可见的事实预测（主手、旁观、冷却、弹数），因此客户端对被拒绝的步枪蓄力也永远不会开火。
     */
    public static M1903A4Rules.Decision decide(PlayerEntity user, ItemStack rifle, Hand hand) {
        boolean mainHand = hand == Hand.MAIN_HAND && user.getMainHandStack() == rifle
                && rifle.getItem() instanceof M1903A4Item;
        boolean coolingDown = user.getItemCooldownManager().isCoolingDown(rifle.getItem());
        boolean loaded = M1903A4Item.getLoadedRounds(rifle) > 0;
        if (!(user instanceof ServerPlayerEntity shooter)) {
            return M1903A4Rules.decide(new M1903A4Rules.Facts(OffMatchUse.Mode.MATCH, mainHand, user.isSpectator(),
                    false, false, coolingDown, loaded));
        }
        OffMatchUse.Mode mode = OffMatchUse.mode(shooter,
                GameFunctions.isPlayerPlayingAndAlive(shooter) && !shooter.isSpectator());
        return M1903A4Rules.decide(new M1903A4Rules.Facts(mode, mainHand, shooter.isSpectator(), isHeld(shooter),
                mainHand && SparkTraitsKillerBridge.blocksWeaponAction(shooter, rifle), coolingDown, loaded));
    }

    /**
     * The Savior pistol's refusal set minus the SparkTraits gate (asked separately): Control Expert stun, Seeker remote
     * view, Kidnapper control, an active Wraith, a running Dominator forced-shot sequence. SparkStrength control
     * (Engineer stun, Taotie daze) has no SparkFactionAPI query and is not seen here.
     * 救世主手枪的拒绝集合（SparkTraits 门槛另行询问）：控场专家眩晕、搜寻者远程视角、绑匪操控、激活的冤魂、进行中的支配者强制
     * 开枪序列。SparkStrength 的控制（工程师眩晕、饕餮眩晕）没有 SparkFactionAPI 查询，这里看不到。
     */
    private static boolean isHeld(ServerPlayerEntity shooter) {
        return ControlExpertStun.isStunned(shooter)
                || SeekerRemoteSessionService.isLocked(shooter)
                || KidnapperControlComponent.KEY.maybeGet(shooter).map(KidnapperControlComponent::isControlled)
                .orElse(false)
                || WraithStateService.isActive(shooter)
                || DominatorForcedShotGuns.refusesHandShot(shooter);
    }

    /**
     * Server half of one accepted shot, after the round was spent and the cooldown written: resolve, smoke, replay
     * (recorded before the kill, like Wathe's gun), then the one kill and its shooter-only confirmation.
     * 一次被接受射击的服务端部分，在耗弹与写入冷却之后：结算、烟迹、回放（与 Wathe 枪械一样在击杀之前记录），然后是唯一一次击杀
     * 及只有射手听得到的确认音效。
     */
    static void fire(ServerPlayerEntity shooter, ItemStack rifle) {
        ServerWorld world = shooter.getServerWorld();
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        Vec3d start = shooter.getEyePos();
        Vec3d rayEnd = rayEnd(shooter, start);
        // Players the shooter may not affect are filtered before the nearest pick, so they are transparent; the shooter
        // aimed at its delayed client view, so the server tests rewound volumes.
        // 射手无法影响的玩家在选取最近者之前就被过滤，因此对射线透明；射手瞄准的是客户端的延迟画面，因此服务端检测回溯后的体积。
        ServerPlayerEntity picked = ControlExpertTaserTargeting.findTarget(shooter, RANGE, world.getPlayers(),
                candidate -> isEligible(shooter, candidate, game),
                candidate -> PlayerHitboxHistory.hitVolumes(shooter, candidate, M1903A4Rules.HIT_MARGIN));
        Hit hit = resolve(shooter, rifle, picked, start, rayEnd);
        ServerPlayerEntity victim = hit.victim();

        RangedSmokeCarve.ray(world, start, hit.stop()); // BEAR smoke: the round's tunnel / BEAR 烟雾：子弹的通道
        spawnSmoke(world, start, hit.stop());
        NbtCompound extra = new NbtCompound();
        extra.putBoolean(M1903A4Rules.RECORD_HIT, victim != null);
        GameRecordManager.recordItemUse(shooter, SparkWitchItems.M1903A4_ID, victim, extra);

        if (victim != null && strike(shooter, victim, game)) {
            // Shooter only: bystanders must not learn the outcome, nor hear a Fiend / Charon feign fail.
            // 只给射手：旁观者不得得知结果，也不得听出魔人 / 卡戎假死。
            shooter.playSoundToPlayer(SparkWitchSounds.M1903A4_KILL, SoundCategory.PLAYERS, 1.0F, 1.0F);
        }
    }

    /**
     * What the round met and where it stopped. {@code victim} is the player to kill (a crow's owner included), null for
     * a miss or an absorbed round. / 子弹碰到了什么、停在哪里。{@code victim} 为要击杀的玩家（含乌鸦的主人），未命中或被吸收
     * 时为 null。
     */
    record Hit(@Nullable ServerPlayerEntity victim, Vec3d stop) {
    }

    /**
     * Nearest wins along the one rewound eye ray, in the Sphinx Hao fist's (and Hunter centre ray's) order: a Raven
     * Form crow nearer than the picked player, every puppet and every breakable device is hit and its owner becomes the
     * victim; else a nearer Magician puppet ends (no kill, the Magician is paid); else the Seeker seam, which
     * SparkStrength may also hook by name: a nearer breakable Seeker device breaks, a Partygoer dies or a Bomber drone
     * breaks, and null comes back. Read-only queries first, then exactly one write.
     * 沿唯一一条回溯眼睛射线的最近者命中，顺序与狮身豪拳头（及猎人猎枪中心射线）一致：比所选玩家、所有皮套与所有可打坏设备都更近
     * 的化鸦乌鸦被命中，其主人成为受害者；否则更近的魔术师皮套被结束（未击杀，魔术师获得报酬）；否则进入搜寻者接缝，
     * SparkStrength 也可按方法名挂接：更近且可打坏的搜寻者设备被打坏、派对客死亡或轰炸者无人机被击毁，并返回 null。先做只读查询，
     * 然后恰好一次写入。
     */
    private static Hit resolve(ServerPlayerEntity shooter, ItemStack rifle, @Nullable ServerPlayerEntity picked,
                               Vec3d start, Vec3d rayEnd) {
        BlackRavenCrowHits.ShotgunHit crow = BlackRavenCrowHits.onM1903A4Fired(shooter, picked, start, rayEnd,
                M1903A4Rules.HIT_MARGIN);
        if (crow != null) {
            return new Hit(crow.owner(), along(start, rayEnd, Math.sqrt(crow.entryDistanceSquared())));
        }
        MagicianPlaybackEntity puppet = MagicianPuppetHits.m1903a4PuppetOnRay(shooter, picked, start, rayEnd);
        if (puppet != null) {
            Vec3d stop = entryPoint(start, rayEnd, List.of(puppet.getBoundingBox()), puppet.getBoundingBox().getCenter());
            MagicianPuppetHits.endM1903A4Puppet(shooter, rifle, puppet);
            return new Hit(null, stop);
        }
        SeekerDeviceEntity device = SeekerDeviceHits.m1903a4Absorber(shooter, picked, RANGE);
        Vec3d deviceStop = device == null ? null
                : entryPoint(start, rayEnd, List.of(device.getBoundingBox()), device.getBoundingBox().getCenter());
        // Frozen seam, once per shot, full range: SparkStrength may hook it by name (Partygoer / Bomber drone, cut at the
        // first block); null means a device, a Partygoer or a drone took the round.
        // 冻结接缝，每发一次、满射程：SparkStrength 可按方法名挂接（派对客 / 轰炸者无人机，在第一个方块处截断）；返回 null 表示
        // 设备、派对客或无人机吃下了子弹。
        ServerPlayerEntity target = SeekerDeviceHits.onM1903A4Fired(shooter, picked, RANGE);
        if (target != null) {
            return new Hit(target, impactPoint(shooter, target, start, rayEnd));
        }
        // A broken device stops the round at its box; a SparkStrength absorber has no position here, so the ray's end.
        // 被打坏的设备让子弹停在其箱体处；SparkStrength 吸收者在此没有位置信息，因此停在射线终点。
        boolean deviceTook = device != null && device.isRemoved();
        return new Hit(null, deviceTook ? deviceStop : rayEnd);
    }

    /**
     * One non-forced kill with Wathe's gun reason; true only for a real terminal death.
     * 一次以 Wathe 枪击死因进行的非强制击杀；只有真正的最终死亡才返回 true。
     */
    private static boolean strike(ServerPlayerEntity shooter, ServerPlayerEntity victim, GameWorldComponent game) {
        boolean aliveBefore = GameFunctions.isPlayerPlayingAndAlive(victim);
        GameFunctions.killPlayer(victim, true, shooter, GameConstants.DeathReasons.GUN);
        return M1903A4Rules.confirmsKill(true, aliveBefore, game.isPlayerDead(victim.getUuid()),
                SparkTraitsGunBridge.deathWasIntercepted(victim));
    }

    /**
     * Eligibility before geometry: never the shooter, only a living role holder (no spectator, creative or active
     * Wraith), honouring SparkFactionAPI's structural veto, SparkTraits Last Escape and Vendetta isolation.
     * 资格先于几何：永不选中射手本人，只选存活的职业持有者（非旁观、非创造、非激活冤魂），并遵守 SparkFactionAPI 结构性否决、
     * SparkTraits 绝处逢生与复仇者隔离。
     */
    static boolean isEligible(ServerPlayerEntity shooter, ServerPlayerEntity candidate, GameWorldComponent game) {
        if (candidate == shooter || candidate.getUuid().equals(shooter.getUuid())) {
            return false;
        }
        return RoundParticipants.ROLE_HOLDER.test(candidate, game)
                && SparkFactionApi.canAffectPlayer(shooter, candidate, GameConstants.DeathReasons.GUN, game)
                && !SparkTraitsKillerBridge.isLastEscapeActive(candidate)
                && M1903A4Rules.vendettaAllows(VendettaInteractionService.isActiveVendetta(shooter),
                VendettaInteractionService.isActiveVendetta(candidate),
                VendettaInteractionService.isExactPair(shooter, candidate));
    }

    /** The eye ray's end: the first collider block, else the full range. / 眼睛射线终点：第一个碰撞方块，否则为满射程。 */
    private static Vec3d rayEnd(PlayerEntity shooter, Vec3d start) {
        Vec3d end = start.add(shooter.getRotationVec(1.0F).multiply(RANGE));
        BlockHitResult block = shooter.getWorld().raycast(new RaycastContext(
                start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, shooter));
        return block.getType() == HitResult.Type.MISS ? end : block.getPos();
    }

    private static Vec3d along(Vec3d start, Vec3d end, double distance) {
        Vec3d ray = end.subtract(start);
        double length = ray.length();
        return length > 0.0 ? start.add(ray.multiply(Math.min(distance, length) / length)) : start;
    }

    /** Where {@code start -> end} enters {@code volumes}, else {@code fallback}. / 射线进入体积之处，否则为备用点。 */
    private static Vec3d entryPoint(Vec3d start, Vec3d end, List<Box> volumes, Vec3d fallback) {
        double squared = HitscanLagRules.entryDistanceSquared(start, end, volumes);
        return squared >= 0.0 ? along(start, end, Math.sqrt(squared)) : fallback;
    }

    /**
     * Where the ray entered the victim's rewound volumes (what the shooter saw), else the victim's chest.
     * 射线进入受害者回溯体积之处（射手所见），否则为受害者胸口。
     */
    private static Vec3d impactPoint(ServerPlayerEntity shooter, ServerPlayerEntity victim, Vec3d start, Vec3d end) {
        return entryPoint(start, end, PlayerHitboxHistory.hitVolumes(shooter, victim, M1903A4Rules.HIT_MARGIN),
                victim.getBoundingBox().getCenter());
    }

    /** White smoke from the eye to where the round stopped. / 从眼睛到子弹停止处的白色烟迹。 */
    private static void spawnSmoke(ServerWorld world, Vec3d from, Vec3d to) {
        Vec3d path = to.subtract(from);
        double length = path.length();
        if (length < 0.1D) {
            return;
        }
        Vec3d step = path.multiply(1.0D / length);
        for (double travelled = SMOKE_STEP; travelled <= length; travelled += SMOKE_STEP) {
            Vec3d point = from.add(step.multiply(travelled));
            world.spawnParticles(ParticleTypes.CLOUD, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        world.spawnParticles(ParticleTypes.CLOUD, to.x, to.y, to.z, 2, 0.02D, 0.02D, 0.02D, 0.0D);
    }
}
