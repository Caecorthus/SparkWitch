package dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.gun;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.compat.SparkTraitsGunBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.entity.SphinxHaoFistEntity;
import dev.caecorthus.sparkwitch.roles.civilian.bear.ricochet.RangedSmokeCarve;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertTaserTargeting;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.device.SeekerDeviceEntity;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteSessionService;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.hit.BlackRavenCrowHits;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperControlComponent;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlaybackEntity;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPuppetHits;
import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.SphinxHaoParticipation;
import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.SphinxHaoPlayerComponent;
import dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.SphinxHaoRules;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.caecorthus.sparkwitch.util.RoundParticipants;
import dev.caecorthus.sparkwitch.util.hitscan.HitscanLagRules;
import dev.caecorthus.sparkwitch.util.hitscan.PlayerHitboxHistory;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Server half of the 「“手”枪」 (owner spec 2026-11-17): load, dry click and the fist shot. The shot is a
 * server-authoritative hitscan along the shooter's aim (vanilla already applied the use packet's rotation): the Taser's
 * eye ray of {@link SphinxHaoRules#GUN_RANGE} cut at the first block, nearest eligible player against lag-compensated
 * volumes, then nearest wins against Raven Form crows, Magician puppets and the Seeker / SparkStrength seam
 * ({@link #resolve}). A hit (a crow's owner included) is ONE non-forced {@code killPlayer} with
 * {@link SparkWitchDeathReasons#SPHINX_HAO_FIST}, so every shield, veto and SparkTraits interception applies. The shooter
 * then gets the KILL or NO_KILL effects ({@link SphinxHaoGunRules#effects}); hao is paid only through
 * {@link SphinxHaoPlayerComponent#addHao}. No innocent-shot punishment, no mood cost (not a Wathe gun).
 * 「“手”枪」的服务端部分（所有者规格 2026-11-17）：装填、空响与拳头射击。射击是沿射手瞄准方向（原版已应用使用数据包的朝向）的
 * 服务端权威即时射线：与电击枪相同的眼睛射线，长 {@link SphinxHaoRules#GUN_RANGE}、在第一个方块处截断，对延迟补偿后的体积
 * 选取最近的合格玩家，然后与化鸦乌鸦、魔术师皮套及搜寻者 / SparkStrength 接缝比较最近者（{@link #resolve}）。命中（含乌鸦的主人）
 * 即一次以 {@link SparkWitchDeathReasons#SPHINX_HAO_FIST}
 * 进行的非强制 {@code killPlayer}，因此所有护盾、否决与 SparkTraits 拦截都生效。随后射手获得击杀或未击杀效果
 * （{@link SphinxHaoGunRules#effects}）；豪度值只经 {@link SphinxHaoPlayerComponent#addHao} 支付。没有误杀惩罚，也不扣理智
 * （不是 Wathe 枪械）。
 */
public final class SphinxHaoGunService {
    static final String NO_AMMO_KEY = "message.sparkwitch.sphinx_hao_hand_gun.no_ammo";
    static final String MISS_KEY = "message.sparkwitch.sphinx_hao_hand_gun.miss";
    /** Replay record key: whether a player took the fist. / 回放记录键：是否有玩家挨了这一拳。 */
    static final String RECORD_HIT = "hit";
    private static final float FIRE_VOLUME = 1.0F;
    private static final float LOAD_PITCH = 1.2F;

    private SphinxHaoGunService() {
    }

    /**
     * Both sides: only a playing, living, non-spectator Sphinx Hao. The server also refuses a holder another authority
     * holds (stun, Seeker remote view, Kidnapper control, active Wraith); the client only predicts the role gate.
     * 双端：只有在局、存活、非旁观的狮身豪。服务端还会拒绝被其他机制控制的持有者（眩晕、搜寻者远程视角、绑匪操控、激活的冤魂）；
     * 客户端只预测职业门槛。
     */
    public static boolean mayUse(@Nullable PlayerEntity user) {
        if (user == null) {
            return false;
        }
        boolean held = user instanceof ServerPlayerEntity server && isHeld(server);
        return SphinxHaoGunRules.mayUse(SphinxHaoParticipation.isSphinxHao(user),
                GameFunctions.isPlayerPlayingAndAlive(user), user.isSpectator(), held);
    }

    private static boolean isHeld(ServerPlayerEntity player) {
        return ControlExpertStun.isStunned(player)
                || SeekerRemoteSessionService.isLocked(player)
                || WraithStateService.isActive(player)
                || KidnapperControlComponent.KEY.maybeGet(player).map(KidnapperControlComponent::isControlled)
                .orElse(false);
    }

    /**
     * One use of {@code gun} by {@code shooter}. False when refused (nothing happened, nothing was spent): the caller
     * answers PASS so vanilla stays silent.
     * {@code shooter} 对 {@code gun} 的一次使用。被拒绝时返回 false（无事发生、无消耗）：调用方返回 PASS，原版保持无声。
     */
    static boolean use(ServerPlayerEntity shooter, ServerWorld world, ItemStack gun) {
        boolean loaded = SphinxHaoHandGunItem.isLoaded(gun);
        ItemStack ammo = loaded ? ItemStack.EMPTY : SphinxHaoGunInventory.firstAmmo(shooter);
        SphinxHaoGunRules.Action action = SphinxHaoGunRules.decide(mayUse(shooter), loaded, !ammo.isEmpty());
        switch (action) {
            case FIRE -> {
                // SparkTraits weapon gate (silence, Last Escape, ...): a blocked shot costs nothing.
                // SparkTraits 武器门槛（沉默、绝处逢生等）：被阻止的射击不产生任何代价。
                if (SparkTraitsKillerBridge.blocksWeaponAction(shooter, gun)) {
                    return false;
                }
                fire(shooter, world, gun);
            }
            case LOAD -> load(shooter, gun, ammo);
            case DRY -> dryClick(shooter);
            case REFUSED -> {
                return false;
            }
        }
        return true;
    }

    private static void load(ServerPlayerEntity shooter, ItemStack gun, ItemStack ammo) {
        ammo.decrement(SphinxHaoGunRules.ammoSpent(SphinxHaoGunRules.Action.LOAD, shooter.isCreative()));
        SphinxHaoHandGunItem.setLoaded(gun, SphinxHaoGunRules.loadedAfter(SphinxHaoGunRules.Action.LOAD, false));
        shooter.getItemCooldownManager().set(gun.getItem(), SphinxHaoRules.GUN_LOAD_COOLDOWN_TICKS);
        shooter.getInventory().markDirty();
        // Only the shooter hears the load, like the Potion Gunner's launcher. / 只有射手听得到装填，与药炮手的炮筒一致。
        shooter.playSoundToPlayer(SoundEvents.ITEM_CROSSBOW_LOADING_END.value(), SoundCategory.PLAYERS, 1.0F,
                LOAD_PITCH);
    }

    /** Empty and no ammo: a click only the shooter hears, plus a hint. / 空膛无子弹：仅射手可闻的空响与提示。 */
    private static void dryClick(ServerPlayerEntity shooter) {
        shooter.playSoundToPlayer(SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.8F, 1.4F);
        shooter.sendMessage(Text.translatable(NO_AMMO_KEY).withColor(SphinxHaoRules.COLOR), true);
    }

    /**
     * The shot. The gun is unloaded and cools down first, so nothing later (a kill, a retaliation) can undo or repeat
     * it. The replay line is recorded before the kill, like Wathe's gun.
     * 一次射击。先退膛并进入冷却，之后的任何事（击杀、反噬）都无法撤销或重复它。回放记录与 Wathe 枪械一样在击杀之前写入。
     */
    private static void fire(ServerPlayerEntity shooter, ServerWorld world, ItemStack gun) {
        SphinxHaoHandGunItem.setLoaded(gun, SphinxHaoGunRules.loadedAfter(SphinxHaoGunRules.Action.FIRE, true));
        shooter.getItemCooldownManager().set(gun.getItem(), SphinxHaoRules.GUN_FIRE_COOLDOWN_TICKS);
        shooter.getInventory().markDirty();

        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        Vec3d start = shooter.getEyePos();
        Vec3d rayEnd = rayEnd(shooter, start);
        // Players the shooter may not affect are filtered before the nearest pick, so they are transparent; the shooter
        // aimed at its delayed client view, so the server tests rewound volumes.
        // 射手无法影响的玩家在选取最近者之前就被过滤，因此对射线透明；射手瞄准的是客户端的延迟画面，因此服务端检测回溯后的体积。
        ServerPlayerEntity picked = ControlExpertTaserTargeting.findTarget(shooter, SphinxHaoRules.GUN_RANGE,
                world.getPlayers(), candidate -> isEligible(shooter, candidate, game),
                candidate -> PlayerHitboxHistory.hitVolumes(shooter, candidate, SphinxHaoGunRules.HIT_MARGIN));
        Hit hit = resolve(shooter, gun, picked, start, rayEnd);
        ServerPlayerEntity target = hit.victim();
        Vec3d stop = hit.stop();

        world.playSound(null, shooter.getX(), shooter.getEyeY(), shooter.getZ(), SoundEvents.BLOCK_PISTON_EXTEND,
                SoundCategory.PLAYERS, FIRE_VOLUME, SphinxHaoGunRules.FIRE_PISTON_PITCH);
        world.playSound(null, shooter.getX(), shooter.getEyeY(), shooter.getZ(), SoundEvents.ITEM_CROSSBOW_SHOOT,
                SoundCategory.PLAYERS, FIRE_VOLUME, SphinxHaoGunRules.FIRE_CROSSBOW_PITCH);
        RangedSmokeCarve.ray(world, start, stop); // BEAR smoke: the fist's tunnel / BEAR 烟雾：拳头的通道
        world.spawnEntity(new SphinxHaoFistEntity(world, shooter, stop, hit.struck()));
        NbtCompound extra = new NbtCompound();
        extra.putBoolean(RECORD_HIT, target != null);
        GameRecordManager.recordItemUse(shooter, SphinxHaoRules.HAND_GUN_ID, target, extra);

        SphinxHaoGunRules.Outcome outcome = target == null
                ? SphinxHaoGunRules.Outcome.NO_KILL : strike(shooter, target, game);
        reward(shooter, outcome);
    }

    /**
     * What the fist met and where it stopped. {@code victim} is the player to kill (a crow's owner included), null for a
     * miss or an absorbed shot; {@code struck} picks the arrival thump (a player, a crow or a puppet).
     * 拳头碰到了什么、停在哪里。{@code victim} 为要击杀的玩家（含乌鸦的主人），未命中或被吸收时为 null；{@code struck} 决定到达
     * 时的击打声（玩家、乌鸦或皮套）。
     */
    record Hit(@Nullable ServerPlayerEntity victim, Vec3d stop, boolean struck) {
    }

    /**
     * Nearest wins along the one rewound eye ray, in the Hunter shotgun's centre-ray order (the other single-ray lethal
     * gun): a Raven Form crow nearer than the picked player, every puppet and every breakable device is hit and its
     * owner becomes the victim (USEC / Hunter substitution, so the outcome is that owner's real kill); else a Magician
     * puppet nearer than the player and every device ends (the shot is a NO_KILL, the Magician is paid); else the Seeker
     * seam, which SparkStrength may also hook by name: a nearer breakable Seeker device breaks, a Partygoer dies or a
     * Bomber drone breaks, and null comes back (NO_KILL). Read-only queries first, then exactly one write.
     * 沿唯一一条回溯眼睛射线的最近者命中，顺序与猎人猎枪中心射线（另一把单射线致命枪械）一致：比所选玩家、所有皮套与所有可打坏
     * 设备都更近的化鸦乌鸦被命中，其主人成为受害者（USEC / 猎枪的替身机制，因此结果就是对该主人的真实击杀）；否则比玩家与所有设备
     * 都更近的魔术师皮套被结束（这一枪算未击杀，魔术师获得报酬）；否则进入搜寻者接缝，SparkStrength 也可按方法名挂接：更近且可
     * 打坏的搜寻者设备被打坏、派对客死亡或轰炸者无人机被击毁，并返回 null（未击杀）。先做只读查询，然后恰好一次写入。
     */
    private static Hit resolve(ServerPlayerEntity shooter, ItemStack gun, @Nullable ServerPlayerEntity picked,
                               Vec3d start, Vec3d rayEnd) {
        BlackRavenCrowHits.ShotgunHit crow = BlackRavenCrowHits.onSphinxHaoFistFired(shooter, picked, start, rayEnd,
                SphinxHaoGunRules.HIT_MARGIN);
        if (crow != null) {
            return new Hit(crow.owner(), along(start, rayEnd, Math.sqrt(crow.entryDistanceSquared())), true);
        }
        MagicianPlaybackEntity puppet = MagicianPuppetHits.sphinxHaoFistPuppetOnRay(shooter, picked, start, rayEnd);
        if (puppet != null) {
            Vec3d stop = entryPoint(start, rayEnd, List.of(puppet.getBoundingBox()), puppet.getBoundingBox().getCenter());
            MagicianPuppetHits.endSphinxHaoFistPuppet(shooter, gun, puppet);
            return new Hit(null, stop, true);
        }
        SeekerDeviceEntity device = SeekerDeviceHits.sphinxHaoFistAbsorber(shooter, picked, SphinxHaoRules.GUN_RANGE);
        Vec3d deviceStop = device == null ? null
                : entryPoint(start, rayEnd, List.of(device.getBoundingBox()), device.getBoundingBox().getCenter());
        // Frozen seam, once per shot, full range: SparkStrength may hook it by name (Partygoer / Bomber drone, cut at the
        // first block); null means a device, a Partygoer or a drone took the fist.
        // 冻结接缝，每发一次、满射程：SparkStrength 可按方法名挂接（派对客 / 轰炸者无人机，在第一个方块处截断）；返回 null 表示
        // 设备、派对客或无人机吃下了拳头。
        ServerPlayerEntity target = SeekerDeviceHits.onSphinxHaoFistFired(shooter, picked, SphinxHaoRules.GUN_RANGE);
        if (target != null) {
            return new Hit(target, impactPoint(shooter, target, start, rayEnd), true);
        }
        // A broken device stops the fist at its box; a SparkStrength absorber has no position here, so the ray's end.
        // 被打坏的设备让拳头停在其箱体处；SparkStrength 吸收者在此没有位置信息，因此停在射线终点。
        boolean deviceTook = device != null && device.isRemoved();
        return new Hit(null, deviceTook ? deviceStop : rayEnd, false);
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
     * One non-forced kill; KILL only when the victim was alive before, is dead after, and SparkTraits did not intercept
     * the death. / 一次非强制击杀；只有受害者此前存活、之后死亡且 SparkTraits 未拦截死亡时才算击杀。
     */
    private static SphinxHaoGunRules.Outcome strike(ServerPlayerEntity shooter, ServerPlayerEntity victim,
                                                    GameWorldComponent game) {
        boolean aliveBefore = GameFunctions.isPlayerPlayingAndAlive(victim);
        GameFunctions.killPlayer(victim, true, shooter, SparkWitchDeathReasons.SPHINX_HAO_FIST);
        return SphinxHaoGunRules.outcome(true, aliveBefore, game.isPlayerDead(victim.getUuid()),
                SparkTraitsGunBridge.deathWasIntercepted(victim));
    }

    /**
     * The shooter's effects, only while it is still a living participant (a retaliation may have killed it).
     * 射手的效果，仅在其仍是存活参与者时施加（反噬可能已杀死射手）。
     */
    private static void reward(ServerPlayerEntity shooter, SphinxHaoGunRules.Outcome outcome) {
        if (!GameFunctions.isPlayerPlayingAndAlive(shooter) || shooter.isSpectator()) {
            return;
        }
        SphinxHaoGunRules.ShooterEffects effects = SphinxHaoGunRules.effects(outcome);
        if (effects.hao() > 0) {
            SphinxHaoPlayerComponent.KEY.get(shooter).addHao(effects.hao());
        }
        // No particles: the Glowing outline is the owner's tell, not a Speed swirl. / 无粒子：所有者要的破绽是发光轮廓。
        if (effects.speedTicks() > 0) {
            shooter.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, effects.speedTicks(),
                    effects.speedAmplifier(), false, false, true));
        }
        if (effects.glowTicks() > 0) {
            shooter.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, effects.glowTicks(), 0,
                    false, false, true));
        }
        if (effects.missMessage()) {
            shooter.sendMessage(Text.translatable(MISS_KEY).withColor(SphinxHaoRules.COLOR), true);
        }
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
                && SparkFactionApi.canAffectPlayer(shooter, candidate, SparkWitchDeathReasons.SPHINX_HAO_FIST, game)
                && !SparkTraitsKillerBridge.isLastEscapeActive(candidate)
                && vendettaAllows(VendettaInteractionService.isActiveVendetta(shooter),
                VendettaInteractionService.isActiveVendetta(candidate),
                VendettaInteractionService.isExactPair(shooter, candidate));
    }

    /** Pure Vendetta isolation: a Vendetta pair only reaches each other. / 纯复仇者隔离：复仇配对只能互相影响。 */
    static boolean vendettaAllows(boolean shooterVendetta, boolean targetVendetta, boolean exactPair) {
        return !(shooterVendetta || targetVendetta) || exactPair;
    }

    /** The eye ray's end: the first collider block, else the full range. / 眼睛射线终点：第一个碰撞方块，否则为满射程。 */
    private static Vec3d rayEnd(ServerPlayerEntity shooter, Vec3d start) {
        Vec3d end = start.add(shooter.getRotationVec(1.0F).multiply(SphinxHaoRules.GUN_RANGE));
        BlockHitResult block = shooter.getWorld().raycast(new RaycastContext(
                start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, shooter));
        return block.getType() == HitResult.Type.MISS ? end : block.getPos();
    }

    /**
     * Where the ray entered the victim's rewound volumes (what the shooter saw), else the victim's chest.
     * 射线进入受害者回溯体积之处（射手所见），否则为受害者胸口。
     */
    private static Vec3d impactPoint(ServerPlayerEntity shooter, ServerPlayerEntity victim, Vec3d start, Vec3d end) {
        return entryPoint(start, end, PlayerHitboxHistory.hitVolumes(shooter, victim, SphinxHaoGunRules.HIT_MARGIN),
                victim.getBoundingBox().getCenter());
    }
}
