package dev.caecorthus.sparkwitch.roles.witch.abysslistener.zone;

import it.unimi.dsi.fastutil.longs.LongList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.MultifaceGrowthBlock;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.WorldEvents;

/**
 * Vanilla sculk cues for the Deep Dark Zone (research/04 §5), all server-sent and sampled so a big zone stays quiet
 * enough: landing shriek and bloom, per-block charge while spreading, a Warden heartbeat while holding and charge pops
 * while restoring. Identical whoever stands nearby, so they reveal nothing about players.
 * 深暗领域的原版幽匿表现（research/04 §5），全部由服务端发送并抽样，使大领域也不过分吵闹：落地尖啸与绽放、蔓延时逐块
 * 充能、保持阶段的监守者心跳、恢复时的充能爆裂。无论附近站着谁都完全相同，因此不会泄露玩家信息。
 */
final class DeepDarkZoneCues {
    /** Charge level of the spread event (vanilla 3006 data bits 6+). / 蔓延事件的充能等级。 */
    private static final int SPREAD_CHARGE = 2;
    private static final float SPREAD_EVENT_CHANCE = 0.4F;
    private static final int SPREAD_EVENTS_PER_TICK = 4;
    private static final float SPREAD_SOUND_CHANCE = 0.25F;
    private static final float RESTORE_EVENT_CHANCE = 0.25F;
    private static final int RESTORE_EVENTS_PER_TICK = 2;
    /** About 1.5 s between heartbeats while the zone holds. / 保持阶段约每 1.5 秒一次心跳。 */
    static final int HEARTBEAT_INTERVAL_TICKS = 30;
    private static final float HEARTBEAT_VOLUME = 1.5F;
    private static final int SOUL_COUNT = 6;
    private static final double SOUL_SPREAD = 0.3;
    private static final double SOUL_SPEED = 0.02;

    private DeepDarkZoneCues() {
    }

    /** The flask shatters: shriek particles and sound, catalyst bloom and souls. / 孢瓶碎裂：尖啸、绽放与灵魂粒子。 */
    static void landing(ServerWorld world, BlockPos landing) {
        double x = landing.getX() + 0.5;
        double y = landing.getY() + 0.15;
        double z = landing.getZ() + 0.5;
        world.playSound(null, x, y, z, SoundEvents.ENTITY_SPLASH_POTION_BREAK, SoundCategory.BLOCKS,
                1.0F, 0.8F + world.random.nextFloat() * 0.2F);
        world.syncWorldEvent(null, WorldEvents.SCULK_SHRIEKS, landing, 0);
        world.spawnParticles(ParticleTypes.SCULK_SOUL, x, y, z, SOUL_COUNT, SOUL_SPREAD, 0.1, SOUL_SPREAD,
                SOUL_SPEED);
        world.playSound(null, x, y, z, SoundEvents.BLOCK_SCULK_CATALYST_BLOOM, SoundCategory.BLOCKS,
                2.0F, 0.6F + world.random.nextFloat() * 0.4F);
    }

    /** Sampled sculk charge on the open faces of newly converted blocks. / 对新转换方块的外露面抽样播放充能。 */
    static void spread(ServerWorld world, LongList shown) {
        if (shown.isEmpty()) {
            return;
        }
        Random random = world.random;
        int events = 0;
        for (int i = 0; i < shown.size() && events < SPREAD_EVENTS_PER_TICK; i++) {
            if (random.nextFloat() >= SPREAD_EVENT_CHANCE) {
                continue;
            }
            BlockPos pos = BlockPos.fromLong(shown.getLong(i));
            List<Direction> openFaces = openFaces(world, pos);
            if (openFaces.isEmpty()) {
                continue;
            }
            int data = (SPREAD_CHARGE << 6) | MultifaceGrowthBlock.directionsToFlag(openFaces);
            world.syncWorldEvent(null, WorldEvents.SCULK_CHARGE, pos, data);
            events++;
        }
        if (random.nextFloat() < SPREAD_SOUND_CHANCE) {
            BlockPos pos = BlockPos.fromLong(shown.getLong(random.nextInt(shown.size())));
            world.playSound(null, pos, SoundEvents.BLOCK_SCULK_SPREAD, SoundCategory.BLOCKS,
                    1.0F, 0.8F + random.nextFloat() * 0.4F);
        }
    }

    /** A Warden heartbeat at the centre of each holding zone. / 每个处于保持阶段的领域中心播放心跳。 */
    static void hold(ServerWorld world, Iterable<DeepDarkZoneState.Zone> zones, long now) {
        long holdStart = DeepDarkZoneSchedule.holdStartOffset();
        long restoreStart = DeepDarkZoneSchedule.restoreStartOffset();
        for (DeepDarkZoneState.Zone zone : zones) {
            long age = now - zone.landingTick();
            if (age >= holdStart && age < restoreStart && (age - holdStart) % HEARTBEAT_INTERVAL_TICKS == 0) {
                BlockPos center = BlockPos.fromLong(zone.centerPos());
                world.playSound(null, center, SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.BLOCKS,
                        HEARTBEAT_VOLUME, 1.0F);
            }
        }
    }

    /** Sampled charge pops (charge 0) as blocks restore. / 方块恢复时抽样播放充能爆裂（充能为 0）。 */
    static void restore(ServerWorld world, LongList hidden) {
        Random random = world.random;
        int events = 0;
        for (int i = 0; i < hidden.size() && events < RESTORE_EVENTS_PER_TICK; i++) {
            if (random.nextFloat() < RESTORE_EVENT_CHANCE) {
                world.syncWorldEvent(null, WorldEvents.SCULK_CHARGE, BlockPos.fromLong(hidden.getLong(i)), 0);
                events++;
            }
        }
    }

    private static List<Direction> openFaces(ServerWorld world, BlockPos pos) {
        List<Direction> faces = new ArrayList<>(6);
        for (Direction direction : Direction.values()) {
            if (DeepDarkZoneBlocks.passable(world, pos.offset(direction))) {
                faces.add(direction);
            }
        }
        return faces;
    }
}
