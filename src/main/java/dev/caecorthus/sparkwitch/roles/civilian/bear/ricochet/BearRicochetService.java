package dev.caecorthus.sparkwitch.roles.civilian.bear.ricochet;

import dev.caecorthus.sparkwitch.SparkWitchSounds;
import dev.caecorthus.sparkwitch.roles.civilian.bear.helmet.RysTHelmetItem;
import dev.caecorthus.sparkwitch.roles.civilian.bear.helmet.RysTVisor;
import dev.caecorthus.sparkwitch.util.hitscan.RangedOriginScope;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Server authority for the Rys-T ricochet (owner H1-H6, D1-D5), called only by
 * {@code mixin/bear/RicochetKillPlayerMixin} at HEAD of Wathe's 5-arg {@code killPlayer} (priority 1050: after every
 * no-cost immunity guard, before SparkStrength's vest, every {@code KillPlayer.BEFORE} listener, psycho armour, Tofana
 * and the Cat, so a deflected hit spends no shield). It acts when the victim is a living round participant WEARING the
 * helmet in the head slot (D1), the killer is someone else, the reason is ranged (D2) and the kill is not forced. One
 * roll per shot (H2, {@link RicochetMemo}); a ricochet cancels the kill only. Whatever the shooter's hit already
 * decided before {@code killPlayer} stays (H6: Wathe's and USEC's innocent-shot punishment and Wathe's mood loss), and
 * kill-time consequences (SparkTraits Conscience, Q4) do not fire because no kill happened. The wearer gets the daze
 * payload; everyone near hears the ping and sees sparks; a glass hit leaves a crack (D4).
 * Rys-T 跳弹的服务端权威（所有者 H1-H6，D1-D5），只由 {@code mixin/bear/RicochetKillPlayerMixin} 在 Wathe 五参
 * {@code killPlayer} 的 HEAD 调用（优先级 1050：位于所有零代价免疫守卫之后，SparkStrength 护甲、所有
 * {@code KillPlayer.BEFORE} 监听、疯魔护甲、托法娜与猫之前，因此被弹开的命中不消耗任何护盾）。仅当受害者是头部槽佩戴着头盔
 * 的存活对局参与者（D1）、击杀者另有其人、死因属远程（D2）且非强制击杀时生效。每一枪只掷一次骰（H2，{@link RicochetMemo}）；
 * 跳弹只取消击杀。射手命中时在 {@code killPlayer} 之前已经决定的一切保持不变（H6：Wathe 与 USEC 的误伤惩罚、Wathe 的理智扣除），
 * 击杀时才触发的后果（SparkTraits 善良，Q4）因未发生击杀而不触发。佩戴者收到眩晕数据包；附近所有人听到跳弹声并看到火花；
 * 命中玻璃时留下裂纹（D4）。
 */
public final class BearRicochetService {
    /** The helmet surface sits this far from the eyes toward the shot. / 头盔表面距眼睛（朝来枪方向）的距离。 */
    private static final double HELMET_SURFACE = 0.3;
    private static final RicochetMemo MEMO = new RicochetMemo();

    private BearRicochetService() {
    }

    /**
     * True when the Rys-T deflects this kill attempt and the caller must cancel it. Server thread only.
     * Rys-T 弹开本次击杀尝试、调用方必须取消时返回 true。仅限服务端线程。
     */
    public static boolean interceptKill(ServerPlayerEntity victim, @Nullable ServerPlayerEntity killer,
                                        Identifier deathReason, boolean force) {
        if (force || victim == null || !BearRicochetRules.isRanged(deathReason)
                || killer != null && killer.getUuid().equals(victim.getUuid())) {
            return false;
        }
        ItemStack helmet = victim.getEquippedStack(EquipmentSlot.HEAD);
        if (!(helmet.getItem() instanceof RysTHelmetItem)
                || victim.isSpectator()
                || !GameFunctions.isPlayerPlayingAndAlive(victim)
                || victim.getServer() == null) {
            return false;
        }
        Vec3d origin = BearRicochetRules.resolveOrigin(deathReason, RangedOriginScope.isOpen(),
                RangedOriginScope.current(), SparkStrengthBlastOrigin::current,
                killer == null ? null : killer.getEyePos());
        BearRicochetRules.ShotKey key = new BearRicochetRules.ShotKey(victim.getUuid(),
                killer == null ? null : killer.getUuid(), deathReason, victim.getServer().getTicks(), origin);
        Boolean earlier = MEMO.get(key);
        if (earlier != null) {
            // Heavy Artillery / Second Strike re-entry of the same shot: same answer, no second daze.
            // 同一枪的重炮 / 二连击重入：结果相同，不再眩晕第二次。
            return earlier;
        }
        RysTVisor visor = RysTHelmetItem.visor(helmet);
        boolean front = BearRicochetRules.isFront(victim.getHeadYaw(), victim.getX(), victim.getZ(), origin);
        boolean ricochet = BearRicochetRules.ricochets(visor.down(), front, victim.getRandom()::nextFloat);
        MEMO.put(key, ricochet);
        if (ricochet) {
            present(victim, helmet, origin, BearRicochetRules.hitsGlass(visor.down(), front));
        }
        return ricochet;
    }

    /** Crack (D4), public ping + sparks, the wearer's daze payload (D5). / 裂纹（D4）、公开声音与火花、佩戴者的眩晕数据包（D5）。 */
    private static void present(ServerPlayerEntity wearer, ItemStack helmet, @Nullable Vec3d origin, boolean glass) {
        ServerWorld world = wearer.getServerWorld();
        Vec3d eye = wearer.getEyePos();
        if (glass) {
            RysTVisorCracks.addCrack(wearer, helmet, RysTVisorCracks.glassPoint(wearer, origin));
        }
        RysTRicochetS2CPayload payload = RysTRicochetS2CPayload.of(eye, origin, glass);
        Vec3d impact = payload.hasDirection()
                ? eye.add(payload.dx() * HELMET_SURFACE, payload.dy() * HELMET_SURFACE, payload.dz() * HELMET_SURFACE)
                : eye.add(0.0, 0.15, 0.0);
        float pitch = 0.95F + wearer.getRandom().nextFloat() * 0.1F;
        world.playSound(null, impact.x, impact.y, impact.z,
                glass ? SparkWitchSounds.BEAR_RYS_T_VISOR_HIT : SparkWitchSounds.BEAR_RYS_T_RICOCHET,
                SoundCategory.PLAYERS, BearRicochetRules.PUBLIC_SOUND_VOLUME, pitch);
        // Sparks for every onlooker, never inside the wearer's own camera (D5: no flash; the daze is theirs).
        // 火花给所有旁观者，从不出现在佩戴者自己的镜头里（D5：没有闪光；佩戴者看到的是眩晕）。
        BlockStateParticleEffect shards = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.GLASS.getDefaultState());
        for (ServerPlayerEntity viewer : world.getPlayers()) {
            if (viewer == wearer) {
                continue;
            }
            world.spawnParticles(viewer, ParticleTypes.CRIT, false, impact.x, impact.y, impact.z, 6, 0.06, 0.06, 0.06,
                    0.35);
            if (glass) {
                world.spawnParticles(viewer, shards, false, impact.x, impact.y, impact.z, 5, 0.05, 0.05, 0.05, 0.1);
            }
        }
        if (ServerPlayNetworking.canSend(wearer, RysTRicochetS2CPayload.ID)) {
            ServerPlayNetworking.send(wearer, payload);
        }
    }
}
