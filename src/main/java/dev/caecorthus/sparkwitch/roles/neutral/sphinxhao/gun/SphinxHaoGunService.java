package dev.caecorthus.sparkwitch.roles.neutral.sphinxhao.gun;

import dev.caecorthus.sparkfactionapi.api.SparkFactionApi;
import dev.caecorthus.sparkwitch.SparkWitchDeathReasons;
import dev.caecorthus.sparkwitch.compat.SparkTraitsGunBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.entity.SphinxHaoFistEntity;
import dev.caecorthus.sparkwitch.roles.civilian.bear.ricochet.RangedSmokeCarve;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertTaserTargeting;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteSessionService;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperControlComponent;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

/**
 * Server half of the 「“手”枪」 (owner spec 2026-11-17): load, dry click and the fist shot. The shot is a
 * server-authoritative hitscan along the shooter's aim (vanilla already applied the use packet's rotation): the Taser's
 * eye ray of {@link SphinxHaoRules#GUN_RANGE} cut at the first block, nearest eligible player against lag-compensated
 * volumes, then the Seeker / SparkStrength seam. A hit is ONE non-forced {@code killPlayer} with
 * {@link SparkWitchDeathReasons#SPHINX_HAO_FIST}, so every shield, veto and SparkTraits interception applies. The shooter
 * then gets the KILL or NO_KILL effects ({@link SphinxHaoGunRules#effects}); hao is paid only through
 * {@link SphinxHaoPlayerComponent#addHao}. No innocent-shot punishment, no mood cost (not a Wathe gun).
 * 「“手”枪」的服务端部分（所有者规格 2026-11-17）：装填、空响与拳头射击。射击是沿射手瞄准方向（原版已应用使用数据包的朝向）的
 * 服务端权威即时射线：与电击枪相同的眼睛射线，长 {@link SphinxHaoRules#GUN_RANGE}、在第一个方块处截断，对延迟补偿后的体积
 * 选取最近的合格玩家，然后经过搜寻者 / SparkStrength 接缝。命中即一次以 {@link SparkWitchDeathReasons#SPHINX_HAO_FIST}
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
        // Seeker seam: a nearer breakable Seeker device absorbs the fist and breaks. SparkStrength may hook this same
        // call by name (a Partygoer or Bomber drone takes the fist); null means something else took it: NO_KILL.
        // 搜寻者接缝：更近且可打坏的搜寻者设备吸收拳头并被打坏。SparkStrength 可按方法名挂接同一调用（派对客或轰炸者无人机吃下
        // 拳头）；返回 null 表示被其他东西吃下：未击杀。
        ServerPlayerEntity target = SeekerDeviceHits.onSphinxHaoFistFired(shooter, picked, SphinxHaoRules.GUN_RANGE);
        Vec3d stop = target == null ? rayEnd : impactPoint(shooter, target, start, rayEnd);

        world.playSound(null, shooter.getX(), shooter.getEyeY(), shooter.getZ(), SoundEvents.BLOCK_PISTON_EXTEND,
                SoundCategory.PLAYERS, FIRE_VOLUME, SphinxHaoGunRules.FIRE_PISTON_PITCH);
        world.playSound(null, shooter.getX(), shooter.getEyeY(), shooter.getZ(), SoundEvents.ITEM_CROSSBOW_SHOOT,
                SoundCategory.PLAYERS, FIRE_VOLUME, SphinxHaoGunRules.FIRE_CROSSBOW_PITCH);
        RangedSmokeCarve.ray(world, start, stop); // BEAR smoke: the fist's tunnel / BEAR 烟雾：拳头的通道
        world.spawnEntity(new SphinxHaoFistEntity(world, shooter, stop, target != null));
        NbtCompound extra = new NbtCompound();
        extra.putBoolean(RECORD_HIT, target != null);
        GameRecordManager.recordItemUse(shooter, SphinxHaoRules.HAND_GUN_ID, target, extra);

        SphinxHaoGunRules.Outcome outcome = target == null
                ? SphinxHaoGunRules.Outcome.NO_KILL : strike(shooter, target, game);
        reward(shooter, outcome);
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
        double squared = HitscanLagRules.entryDistanceSquared(start, end,
                PlayerHitboxHistory.hitVolumes(shooter, victim, SphinxHaoGunRules.HIT_MARGIN));
        if (squared >= 0.0) {
            return start.add(end.subtract(start).normalize().multiply(Math.sqrt(squared)));
        }
        return victim.getBoundingBox().getCenter();
    }
}
