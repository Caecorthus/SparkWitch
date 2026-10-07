package dev.caecorthus.sparkwitch.roles.witch.potiongunner.launcher;

import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.net.FirePotionLauncherC2SPacket;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteSessionService;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerReplay;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerRules;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionShellType;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.PotionShellEntity;
import dev.caecorthus.sparkwitch.util.OffMatchUse;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/**
 * Server-authoritative launcher fire. The client only states intent and aim; this service resolves the shot's
 * {@link OffMatchUse} mode and validates the held and loaded launcher, cooldown, stun/session locks, and SparkTraits
 * weapon blocks before spawning a shell. Use never checks the role (owner rule 2026-10-04): a living participant of an
 * exactly {@code ACTIVE} round fires a full match shot, a dead one nothing, and anyone else a presentation-only one.
 * Fabric runs play-payload receivers on the server thread ({@code ServerPlayNetworkAddon} hands them to
 * {@code MinecraftServer#execute}, which the stun and Seeker payload guards wrap), so no extra hand-off is needed.
 * 服务端权威的炮筒发射。客户端只表达意图与朝向；本服务判定该次射击的 {@link OffMatchUse} 模式，并复核手持且已装填的
 * 炮筒、冷却、眩晕/遥控锁定与 SparkTraits 武器封锁后才生成炮弹。使用从不检查职业（所有者规则 2026-10-04）：恰为
 * {@code ACTIVE} 的对局中存活的参与者打出完整的对局射击，该对局中已死亡的参与者无法发射，其他人打出仅表现的射击。
 * Fabric 在服务端线程上执行游戏数据包接收器（{@code ServerPlayNetworkAddon} 把它们交给
 * 眩晕与搜寻者拦截所包装的 {@code MinecraftServer#execute}），因此无需额外切换线程。
 */
public final class PotionLauncherFireService {
    static final String NOT_LOADED_KEY = "message.sparkwitch.potion_gunner.not_loaded";

    private PotionLauncherFireService() {
    }

    public static void fire(ServerPlayerEntity player, FirePotionLauncherC2SPacket payload) {
        if (player == null || payload == null) {
            return;
        }
        OffMatchUse.Mode mode = OffMatchUse.mode(player);
        ItemStack launcher = player.getMainHandStack();
        boolean holdingLauncher = launcher.getItem() instanceof PotionLauncherItem;
        PotionLauncherFireRules.Decision decision = PotionLauncherFireRules.decide(new PotionLauncherFireRules.Facts(
                mode,
                holdingLauncher,
                player.isSpectator(),
                ControlExpertStun.isStunned(player),
                SeekerRemoteSessionService.isLocked(player),
                holdingLauncher && SparkTraitsKillerBridge.blocksWeaponAction(player, launcher),
                holdingLauncher && player.getItemCooldownManager().isCoolingDown(launcher.getItem()),
                PotionLauncherLoad.isLoaded(launcher)));
        switch (decision) {
            case FIRE -> launch(player, launcher, payload, mode);
            case NOT_LOADED -> dryClick(player);
            default -> {
            }
        }
    }

    /**
     * Spawns the shell first; only a spawned shell clears the load, starts the anti-double-click cooldown, plays the
     * loud launch sound to everyone nearby (counterplay information), records one replay line, and vents the
     * backblast. A failed launch keeps the shell loaded. The cooldown is a plain vanilla write: it is the holder's own
     * launcher. A presentation shot does the same except that its shell and backblast touch nobody and it records no
     * replay line, since it belongs to no match.
     * 先生成炮弹；只有成功生成才清除装填、开始防连点冷却、向附近所有人播放响亮的发射声（给对手的反制信息）、记录一条回放
     * 并喷出尾焰。发射失败则保留已装填的炮弹。冷却直接用原版写入：这是持有者自己的炮筒。仅表现的射击流程相同，只是其炮弹与
     * 尾焰不触及任何人，也不记录回放，因为它不属于任何对局。
     */
    private static void launch(ServerPlayerEntity player, ItemStack launcher, FirePotionLauncherC2SPacket payload,
                               OffMatchUse.Mode mode) {
        PotionShellType type = PotionLauncherLoad.loaded(launcher).orElse(null);
        if (type == null) {
            return;
        }
        boolean presentation = mode == OffMatchUse.Mode.PRESENTATION;
        PotionLauncherFireRules.Aim aim = PotionLauncherFireRules.aim(payload.hasAim(), payload.yaw(),
                payload.pitch(), player.getYaw(), player.getPitch());
        if (!PotionShellEntity.launch(player, type, aim.yaw(), aim.pitch(), presentation)) {
            return;
        }
        PotionLauncherLoad.setLoaded(launcher, null);
        player.getItemCooldownManager().set(launcher.getItem(), PotionGunnerRules.FIRE_COOLDOWN_TICKS);
        ServerWorld world = player.getServerWorld();
        world.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 3.0F, 0.55F);
        world.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.7F, 1.6F);
        if (!presentation) {
            GameRecordManager.recordItemUse(player, PotionGunnerRules.FIRE_REPLAY_ID, null,
                    PotionGunnerReplay.fireData(type));
        }
        // Owner rule: every launched shell vents a backblast straight behind the shooter (after the shot's replay
        // line); D-R2: the lane follows the yaw only. / 所有者规则：每颗射出的炮弹都会在射手正后方喷出尾焰
        // （在该次发射的回放行之后）；D-R2：通道只跟随偏航角。
        PotionBackblastService.fire(player, aim.yaw(), presentation);
    }

    /** Empty launcher: a dry click only the shooter hears, plus an action-bar hint. / 未装填：仅射手可闻的空响与动作栏提示。 */
    private static void dryClick(ServerPlayerEntity player) {
        player.playSoundToPlayer(SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.8F, 1.2F);
        player.sendMessage(Text.translatable(NOT_LOADED_KEY).withColor(PotionGunnerRules.COLOR), true);
    }
}
