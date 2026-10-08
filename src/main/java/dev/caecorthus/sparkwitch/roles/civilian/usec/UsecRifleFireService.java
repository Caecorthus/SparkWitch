package dev.caecorthus.sparkwitch.roles.civilian.usec;

import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.compat.SparkTraitsUsecBridge;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteSessionService;
import dev.caecorthus.sparkwitch.roles.civilian.usec.net.FireUsecRifleC2SPacket;
import dev.caecorthus.sparkwitch.roles.civilian.usec.net.UsecBulletImpactsS2CPacket;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperControlComponent;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPuppetHits;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithStateService;
import dev.caecorthus.sparkwitch.util.OffMatchUse;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.record.GameRecordManager;
import dev.doctor4t.wathe.util.Scheduler;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Server-authoritative USEC rifle fire (WP3). The client sends only {@code sparkwitch:fire_usec_rifle} with its
 * click-time aim, trusted for direction only (Death Ray rule); this service re-checks everything in the fixed order of
 * {@link UsecFireRules#decide}, cycles the bolt, plays the shot, traces the bullet ({@link UsecTracer}) and resolves
 * nearest-wins along the whole path: a Magician puppet, then a Seeker device, then the lag-compensated player. Use never
 * checks the role (owner rule 2026-10-04, {@link OffMatchUse}): a living participant of an ACTIVE round fires a match
 * shot, a dead one nothing, anyone else a presentation shot (sound, particles and cracks; no kill, device break or
 * replay line). Fabric runs play-payload receivers on the server thread, behind the stun, Seeker-session and Rift-session
 * payload guards, so no extra hand-off is needed.
 * 服务端权威的 USEC 步枪开火（WP3）。客户端只发送带点击瞬间朝向的 {@code sparkwitch:fire_usec_rifle}，朝向只被信任于方向
 * （死亡射线规则）；本服务按 {@link UsecFireRules#decide} 的固定顺序复核一切、拉栓、播放枪声、追踪子弹（{@link UsecTracer}），
 * 并沿整条路径结算最近者命中：依次为魔术师皮套、搜寻者设备、延迟补偿后的玩家。使用从不检查职业（所有者规则 2026-10-04，
 * {@link OffMatchUse}）：ACTIVE 对局中存活的参与者打出对局射击，已死亡的参与者无法开火，其他人打出表现射击（声音、粒子与裂痕；
 * 不击杀、不打坏设备、不记录回放）。Fabric 在服务端线程上执行游戏数据包接收器，位于眩晕、搜寻者会话与裂隙门会话拦截之后，
 * 因此无需额外切换线程。
 */
public final class UsecRifleFireService {
    static final String CHAMBER_EMPTY_KEY = "message.sparkwitch.usec.chamber_empty";
    /** Muzzle point ahead of the eye. / 枪口点在眼前的距离。 */
    private static final double MUZZLE_OFFSET = 1.0;
    private static boolean registered;

    private UsecRifleFireService() {
    }

    /** Called once from {@link UsecFeatureService#register()}. / 由 {@link UsecFeatureService#register()} 调用一次。 */
    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerPlayNetworking.registerGlobalReceiver(FireUsecRifleC2SPacket.ID,
                (payload, context) -> fire(context.player(), payload));
        UsecReplay.register();
    }

    public static void fire(ServerPlayerEntity player, FireUsecRifleC2SPacket payload) {
        if (player == null || payload == null) {
            return;
        }
        OffMatchUse.Mode mode = OffMatchUse.mode(player);
        ItemStack rifle = player.getMainHandStack();
        boolean holding = rifle.getItem() instanceof UsecRifleItem;
        UsecRifleState state = holding ? UsecRifleState.read(rifle) : UsecRifleState.EMPTY;
        UsecFireRules.Decision decision = UsecFireRules.decide(new UsecFireRules.Facts(
                mode,
                holding,
                player.isSpectator(),
                ControlExpertStun.isStunned(player),
                SeekerRemoteSessionService.isLocked(player),
                isKidnapped(player),
                WraithStateService.isActive(player),
                holding && SparkTraitsKillerBridge.blocksWeaponAction(player, rifle),
                holding && player.getItemCooldownManager().isCoolingDown(rifle.getItem()),
                state.chamber() != null));
        switch (decision) {
            case FIRE -> shoot(player, rifle, state, payload, mode);
            case EMPTY_CHAMBER -> dryClick(player);
            default -> {
            }
        }
    }

    /**
     * One shot. The bolt cycles first (round consumed, the magazine's top round chambered, bolt cooldown, stack
     * written), so nothing later (a kill, a punishment) can undo or repeat it.
     * 一次射击。先完成拉栓循环（消耗子弹、弹匣顶部子弹上膛、拉栓冷却、写回物品），之后的任何事（击杀、惩罚）都无法撤销或重复它。
     */
    private static void shoot(ServerPlayerEntity player, ItemStack rifle, UsecRifleState state,
                              FireUsecRifleC2SPacket payload, OffMatchUse.Mode mode) {
        UsecFireRules.Cycle cycle = UsecFireRules.cycle(state);
        if (cycle == null) {
            return;
        }
        UsecRifleState.write(rifle, cycle.next());
        UsecCooldowns.bolt(player);

        ServerWorld world = player.getServerWorld();
        Vec3d eye = player.getEyePos();
        Vec3d direction = UsecFireRules.aimDirection(payload.hasAim(), payload.yaw(), payload.pitch(),
                player.getYaw(), player.getPitch());
        playShot(world, player, state.suppressor());
        spawnMuzzle(world, eye, direction, state.suppressor());
        if (cycle.chambered()) {
            scheduleBoltSound(player, world, rifle, cycle.next(), mode);
        }

        UsecTracer.Trace trace = UsecTracer.trace(eye, direction, cycle.fired(),
                SparkTraitsUsecBridge.marksmanRangeMultiplier(player), new UsecTracerWorldProbe(world, player));
        double stop = trace.path().length();
        if (mode == OffMatchUse.Mode.MATCH) {
            stop = resolveMatchHit(player, rifle, cycle.fired(), trace);
        }
        List<UsecImpactRules.Impact> impacts = UsecImpactRules.impacts(eye, trace, stop);
        spawnDebris(world, impacts);
        sendImpacts(world, impacts);
    }

    /**
     * Nearest wins along the path, one measure for all: a puppet strictly nearer than the player and every breakable
     * device ends; else a device strictly nearer than the player breaks; else the player is hit. Records the fire line
     * (before the kill, like Wathe's gun) and returns the path distance where the bullet stopped.
     * 沿路径最近者命中，所有对象同一量法：严格近于玩家及所有可打坏设备的皮套被结束；否则严格近于玩家的设备被打坏；否则命中玩家。
     * 记录开火行（与 Wathe 枪械一样在击杀之前），并返回子弹停下处的路径距离。
     */
    private static double resolveMatchHit(ServerPlayerEntity player, ItemStack rifle, UsecAmmoType ammo,
                                          UsecTracer.Trace trace) {
        UsecShotPath path = trace.path();
        UsecShotTargets.PlayerHit hit = UsecShotTargets.nearestPlayer(player, path);
        double reach = hit == null ? path.length() : Math.min(hit.distance(), path.length());
        double stop = reach;
        ServerPlayerEntity victim = null;
        double puppetEnd = MagicianPuppetHits.onUsecRifleFired(player, rifle, path.points(), reach);
        if (puppetEnd < reach) {
            stop = puppetEnd;
        } else {
            double deviceEnd = SeekerDeviceHits.onUsecRifleFired(player, path.points(), reach);
            if (deviceEnd < reach) {
                stop = deviceEnd;
            } else if (hit != null) {
                victim = hit.player();
            }
        }
        GameRecordManager.recordItemUse(player, UsecReplay.RECORD_ID, victim,
                UsecReplay.fireData(ammo, penetratedBefore(trace, stop)));
        if (victim != null) {
            UsecFirePunishment.killAndPunish(player, victim, ammo);
        }
        return stop;
    }

    private static int penetratedBefore(UsecTracer.Trace trace, double stop) {
        int count = 0;
        for (UsecTracer.Penetration penetration : trace.penetrations()) {
            if (penetration.distance() < stop) {
                count++;
            }
        }
        return count;
    }

    /**
     * The public shot: loud, or the suppressed sound at its own volume and pitch. Public so every nearby player,
     * the Blind included, perceives it (the Blind range factors key on the sound id).
     * 公开枪声：响亮枪声，或以消音器自身音量与音调播放的消音枪声。公开播放，附近所有玩家（包括盲人）都能感知（盲人距离系数按声音 id 区分）。
     */
    private static void playShot(ServerWorld world, ServerPlayerEntity player, boolean suppressed) {
        SoundEvent sound = suppressed ? SparkWitchSounds.USEC_RIFLE_SHOOT_SUPPRESSED : SparkWitchSounds.USEC_RIFLE_SHOOT;
        float volume = suppressed ? UsecRules.SUPPRESSED_SHOT_VOLUME : UsecRules.SHOT_VOLUME;
        float pitch = suppressed ? UsecRules.SUPPRESSED_PITCH
                : 1.0F + (player.getRandom().nextFloat() * 2.0F - 1.0F) * UsecFireRules.SHOT_PITCH_SPREAD;
        world.playSound(null, player.getX(), player.getEyeY(), player.getZ(), sound, SoundCategory.PLAYERS, volume,
                pitch);
    }

    /** Smoke always; the flame flash only without a suppressor. / 总有烟雾；只有未装消音器时才有火光。 */
    private static void spawnMuzzle(ServerWorld world, Vec3d eye, Vec3d direction, boolean suppressed) {
        Vec3d muzzle = eye.add(direction.multiply(MUZZLE_OFFSET));
        world.spawnParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, suppressed ? 2 : 5,
                0.04, 0.04, 0.04, 0.01);
        if (!suppressed) {
            world.spawnParticles(ParticleTypes.SMALL_FLAME, muzzle.x, muzzle.y, muzzle.z, 3, 0.03, 0.03, 0.03, 0.02);
        }
    }

    /**
     * The bolt cycles audibly about half a second later when a round was chambered, only if nothing changed since the
     * shot ({@link UsecFireRules#boltSoundDue}): the shooter is still online in that world and not a spectator, still
     * holds that very rifle stack in the main hand with the post-shot state (so an UNLOAD_CHAMBER or any other
     * attachment action in between, which plays its own bolt, never adds a second one), and the round context is the
     * same (the use mode and Wathe's game status).
     * 有子弹上膛时约半秒后播放拉栓声，但仅当射击后一切未变（{@link UsecFireRules#boltSoundDue}）：射手仍在线、仍在该世界且
     * 不是旁观者；主手仍是同一把步枪物品堆且保持射击后的状态（因此期间的退膛或其他配件操作会自行播放拉栓声，绝不会多出第二次）；
     * 对局情境相同（使用模式与 Wathe 游戏状态）。
     */
    private static void scheduleBoltSound(ServerPlayerEntity player, ServerWorld world, ItemStack rifle,
                                          UsecRifleState expected, OffMatchUse.Mode mode) {
        GameWorldComponent.GameStatus status = GameWorldComponent.KEY.get(world).getGameStatus();
        Scheduler.schedule(() -> {
            boolean online = !player.isRemoved() && !player.isDisconnected() && player.getServerWorld() == world
                    && !player.isSpectator();
            ItemStack held = online ? player.getMainHandStack() : ItemStack.EMPTY;
            if (!UsecFireRules.boltSoundDue(online, held == rifle,
                    held == rifle && UsecRifleState.read(held).equals(expected),
                    mode, online ? OffMatchUse.mode(player) : null,
                    status, GameWorldComponent.KEY.get(world).getGameStatus())) {
                return;
            }
            world.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SparkWitchSounds.USEC_RIFLE_BOLT,
                    SoundCategory.PLAYERS, 1.0F, 1.0F);
        }, UsecFireRules.BOLT_SOUND_DELAY_TICKS);
    }

    /** Block debris at every entry (and exit) point, in presentation shots too. / 每个入口（与出口）处的方块碎屑，表现射击同样生成。 */
    private static void spawnDebris(ServerWorld world, List<UsecImpactRules.Impact> impacts) {
        for (UsecImpactRules.Impact impact : impacts) {
            BlockState state = world.getBlockState(impact.pos());
            if (state.isAir()) {
                continue;
            }
            BlockStateParticleEffect debris = new BlockStateParticleEffect(ParticleTypes.BLOCK, state);
            Vec3d point = impact.point();
            world.spawnParticles(debris, point.x, point.y, point.z, 10, 0.08, 0.08, 0.08, 0.15);
            Vec3d exit = impact.exit();
            if (exit != null) {
                world.spawnParticles(debris, exit.x, exit.y, exit.z, 6, 0.08, 0.08, 0.08, 0.15);
            }
        }
    }

    /**
     * One {@code usec_bullet_impacts} packet to every player within {@link UsecImpactRules#RECIPIENT_RADIUS} of any
     * impact; the client draws the cracks and never changes a block.
     * 向距任一命中点 {@link UsecImpactRules#RECIPIENT_RADIUS} 以内的每名玩家发送一个 {@code usec_bullet_impacts} 包；客户端绘制裂痕，
     * 从不改动方块。
     */
    private static void sendImpacts(ServerWorld world, List<UsecImpactRules.Impact> impacts) {
        if (impacts.isEmpty()) {
            return;
        }
        UsecBulletImpactsS2CPacket packet = new UsecBulletImpactsS2CPacket(
                impacts.stream().map(UsecImpactRules.Impact::toPacket).toList());
        for (ServerPlayerEntity recipient : world.getPlayers()) {
            if (UsecImpactRules.isRecipient(recipient.getPos(), impacts)
                    && ServerPlayNetworking.canSend(recipient, UsecBulletImpactsS2CPacket.ID)) {
                ServerPlayNetworking.send(recipient, packet);
            }
        }
    }

    /** Empty chamber: a dry click only the shooter hears, plus an action-bar hint. / 空膛：仅射手可闻的空响与动作栏提示。 */
    private static void dryClick(ServerPlayerEntity player) {
        player.playSoundToPlayer(SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.8F, 1.4F);
        player.sendMessage(Text.translatable(CHAMBER_EMPTY_KEY).withColor(UsecRules.COLOR), true);
    }

    private static boolean isKidnapped(ServerPlayerEntity player) {
        return KidnapperControlComponent.KEY.maybeGet(player).map(KidnapperControlComponent::isControlled)
                .orElse(false);
    }
}
