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
 * enough: landing shriek and bloom, one charge burst over the whole zone as it appears (owner D14), a Warden heartbeat
 * while holding and charge pops while restoring. Identical whoever stands nearby, so they reveal nothing about players.
 * 深暗领域的原版幽匿表现（research/04 §5），全部由服务端发送并抽样，使大领域也不过分吵闹：落地尖啸与绽放、领域出现时
 * 覆盖整个领域的一次充能爆发（D14）、保持阶段的监守者心跳、恢复时的充能爆裂。无论附近站着谁都完全相同，因此不会泄露
 * 玩家信息。
 */
final class DeepDarkZoneCues {
    /** Charge level of the spread event (vanilla 3006 data bits 6+). / 蔓延事件的充能等级。 */
    private static final int SPREAD_CHARGE = 2;
    private static final int SPREAD_EVENTS_PER_TICK = 12;
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

    /**
     * Sampled sculk charge on the open faces of newly converted blocks, plus one spread sound. The whole zone shows in
     * one tick (owner D14), so each sample is jittered inside its own equal slice of the nearest-first list: the
     * charges cover the centre through the rim instead of clustering at the centre.
     * 对新转换方块的外露面抽样播放充能，并播放一次蔓延音效。整个领域在同一刻显示（D14），因此每个样本在由近及远列表的
     * 等分区段内随机取一格：充能从中心一直覆盖到边缘，而不是集中在中心。
     */
    static void spread(ServerWorld world, LongList shown) {
        if (shown.isEmpty()) {
            return;
        }
        Random random = world.random;
        int size = shown.size();
        int samples = Math.min(SPREAD_EVENTS_PER_TICK, size);
        for (int sample = 0; sample < samples; sample++) {
            int index = (int) ((sample + random.nextFloat()) * size / samples);
            BlockPos pos = BlockPos.fromLong(shown.getLong(index));
            List<Direction> openFaces = openFaces(world, pos);
            if (openFaces.isEmpty()) {
                continue;
            }
            int data = (SPREAD_CHARGE << 6) | MultifaceGrowthBlock.directionsToFlag(openFaces);
            world.syncWorldEvent(null, WorldEvents.SCULK_CHARGE, pos, data);
        }
        BlockPos soundPos = BlockPos.fromLong(shown.getLong(random.nextInt(size)));
        world.playSound(null, soundPos, SoundEvents.BLOCK_SCULK_SPREAD, SoundCategory.BLOCKS,
                1.0F, 0.8F + random.nextFloat() * 0.4F);
    }

    /**
     * A Warden heartbeat at the centre of each holding zone. The hold starts on the landing tick (owner D14), but the
     * first beat waits one interval so it does not stack on the landing and spread sounds.
     * 每个处于保持阶段的领域中心播放心跳。保持阶段从落地那一刻开始（D14），但第一次心跳延后一个间隔，避免与落地和蔓延
     * 音效叠在一起。
     */
    static void hold(ServerWorld world, Iterable<DeepDarkZoneState.Zone> zones, long now) {
        long restoreStart = DeepDarkZoneSchedule.restoreStartOffset();
        for (DeepDarkZoneState.Zone zone : zones) {
            long age = now - zone.landingTick();
            if (age > 0 && age < restoreStart && age % HEARTBEAT_INTERVAL_TICKS == 0) {
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
