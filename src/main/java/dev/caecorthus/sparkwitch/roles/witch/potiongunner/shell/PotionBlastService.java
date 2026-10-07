package dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell;

import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeKillAttribution;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.SeekerBreakSource;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPuppetHits;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerRules;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionShellType;
import dev.caecorthus.sparkwitch.roles.witch.riftwalker.session.RiftSessionService;
import dev.caecorthus.sparkwitch.util.OffMatchUse;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheParticles;
import dev.doctor4t.wathe.index.WatheSounds;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Detonates a shell: grenade-style sound and particles, Seeker device breaks, target resolution, the shell effect
 * (inside Judge attribution), and the per-hit gold reward.
 * 引爆炮弹：手雷式音效与粒子、搜寻者设备破坏、目标判定、炮弹效果（在审判官归因内执行）以及每人奖励金币。
 */
public final class PotionBlastService {
    /** Wathe grenade presentation values ({@code GrenadeEntity#onCollision}). / Wathe 手雷的表现参数。 */
    private static final float EXPLODE_VOLUME = 5.0F;
    private static final float EXPLODE_PITCH_SPREAD = 0.1F;
    private static final int DEBRIS_COUNT = 100;
    private static final double PARTICLE_LIFT = 0.1;
    private static final double SMOKE_SPEED = 0.2;
    private static final double DEBRIS_SPEED = 1.0;
    public static final String HIT_REWARD_KEY = "message.sparkwitch.potion_gunner.hit_reward";

    private PotionBlastService() {
    }

    /**
     * Server only; a match shell only while the round is exactly {@code ACTIVE} (D-R1: no kills, gold or bounties after
     * the winner is decided); the shell entity calls it once and discards itself afterwards.
     * The order is fixed: presentation, Seeker devices (never the gunner's own, {@code mayBreak}-gated while the
     * gunner is online), targets, the effect, then the reward checked against the gunner's state after the effect
     * (a gunner whose own TR killed them is paid nothing). An off-match presentation shell ({@code OffMatchUse}) stops
     * after the presentation, in any round state.
     * 仅服务端；对局炮弹仅在对局恰为 {@code ACTIVE} 时（D-R1：胜负已定后不再有击杀、金币或赏金）；由炮弹实体调用一次，之后自行移除。顺序固定：表现、搜寻者设备（从不打坏药炮手自己的设备，
     * 药炮手在线时经 {@code mayBreak} 校验）、目标、效果，最后按效果之后的药炮手状态结算奖励（被自己 TR 炸死的药炮手不得钱）。
     * 场外仅表现的炮弹（{@code OffMatchUse}）在任何对局状态下都只执行表现。
     */
    public static void detonate(PotionShellEntity shell, Vec3d center) {
        if (shell == null || center == null || !(shell.getWorld() instanceof ServerWorld world)
                || !shell.isPresentation() && !PotionShellEntity.isRoundActive(world)) {
            return;
        }
        PotionShellType type = shell.shellTypeOrNull();
        if (type == null) {
            return;
        }
        UUID gunnerUuid = shell.gunnerUuid();
        ServerPlayerEntity gunner = shell.getOwner() instanceof ServerPlayerEntity player ? player : null;

        present(world, shell, center);
        // Off-match shot: sound and particles only, so no Seeker device, target, effect, Judge attribution or reward.
        // 场外射击：只有声音与粒子，因此没有搜寻者设备、目标、效果、审判官归因或奖励。
        if (shell.isPresentation()) {
            return;
        }
        SeekerDeviceHits.onBlast(world, center, PotionBlastRings.half(type.size()), gunner,
                SeekerBreakSource.POTION_SHELL);
        // Magician seam: a TR blast ends the puppets its player rule would catch. / 魔术师接缝：TR 爆炸结束其玩家规则会波及的皮套。
        MagicianPuppetHits.onPotionShellBlast(world, center, type, gunner, shell.getStack());

        List<PotionBlastHit> hits = PotionBlastResolver.resolve(world, center, type, gunnerUuid, gunner);
        PotionBlastContext context = new PotionBlastContext(world, center, type, gunnerUuid, gunner, hits);
        // Same cause bridge as the Wathe grenade, so a Judge sentence on the gunner covers TR kills.
        // 与 Wathe 手雷相同的归因桥：审判官对药炮手的判决同样覆盖 TR 击杀。
        if (gunnerUuid != null) {
            JudgeKillAttribution.runWith(world, gunnerUuid, () -> PotionShellEffects.apply(context));
        } else {
            PotionShellEffects.apply(context);
        }
        payReward(world, gunner, PotionBlastRewards.rewardedHits(hits));
    }

    private static void present(ServerWorld world, PotionShellEntity shell, Vec3d center) {
        double y = center.y + PARTICLE_LIFT;
        world.playSound(null, center.x, center.y, center.z, WatheSounds.ITEM_GRENADE_EXPLODE, SoundCategory.PLAYERS,
                EXPLODE_VOLUME, 1.0F + shell.getRandom().nextFloat() * EXPLODE_PITCH_SPREAD - EXPLODE_PITCH_SPREAD / 2.0F);
        // The flash is forced to every player (long range), so a distant gunner sees the impact; smoke and debris
        // keep vanilla's normal range like the grenade. A presentation shell never forces it on a match participant,
        // so an off-match shot stays invisible to a live match beyond normal range.
        // 闪光强制发送给所有玩家（远距离），远处的药炮手也能看到落点；烟雾与碎屑与手雷一样保持原版正常范围。仅表现的炮弹
        // 从不对对局参与者强制发送，因此场外射击在正常范围之外不会被进行中的对局看到。
        for (ServerPlayerEntity viewer : world.getPlayers()) {
            boolean force = !shell.isPresentation() || !OffMatchUse.isMatchParticipant(viewer);
            world.spawnParticles(viewer, WatheParticles.BIG_EXPLOSION, force, center.x, y, center.z, 1, 0.0, 0.0, 0.0,
                    0.0);
        }
        world.spawnParticles(ParticleTypes.SMOKE, center.x, y, center.z, DEBRIS_COUNT, 0.0, 0.0, 0.0, SMOKE_SPEED);
        world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, shell.getStack().copyWithCount(1)),
                center.x, y, center.z, DEBRIS_COUNT, 0.0, 0.0, 0.0, DEBRIS_SPEED);
    }

    private static void payReward(ServerWorld world, @Nullable ServerPlayerEntity gunner, int rewardedHits) {
        GameWorldComponent game = GameWorldComponent.KEY.get(world);
        boolean online = gunner != null && !gunner.isRemoved();
        // An in-gate gunner is an alive spectator (Riftwalker D3) and is still paid; see PotionBlastRewards.notSpectating.
        // 门内的药炮手是存活旁观者（隙行者 D3），照常领取奖励；见 PotionBlastRewards.notSpectating。
        int amount = PotionBlastRewards.amount(
                online,
                online && GameFunctions.isPlayerPlayingAndAlive(gunner)
                        && PotionBlastRewards.notSpectating(gunner.isSpectator(), RiftSessionService.isInside(gunner)),
                online && PotionGunnerRules.isPotionGunner(game.getRole(gunner)),
                rewardedHits);
        if (amount <= 0) {
            return;
        }
        // addToBalance syncs the owner's shop component. / addToBalance 会同步拥有者的商店组件。
        PlayerShopComponent.KEY.get(gunner).addToBalance(amount);
        gunner.sendMessage(Text.translatable(HIT_REWARD_KEY, rewardedHits, amount)
                .withColor(PotionGunnerRules.COLOR), true);
    }
}
