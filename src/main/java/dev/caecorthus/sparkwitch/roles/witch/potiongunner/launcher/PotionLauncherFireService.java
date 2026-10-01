package dev.caecorthus.sparkwitch.roles.witch.potiongunner.launcher;

import dev.caecorthus.sparkwitch.compat.SparkTraitsKillerBridge;
import dev.caecorthus.sparkwitch.net.FirePotionLauncherC2SPacket;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.remote.SeekerRemoteSessionService;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerReplay;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionGunnerRules;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionShellType;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.shell.PotionShellEntity;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/**
 * Server-authoritative launcher fire. The client only states intent and aim; this service validates the shooter,
 * the held and loaded launcher, cooldown, stun/session locks, and SparkTraits weapon blocks before spawning a shell.
 * Fabric runs play-payload receivers on the server thread ({@code ServerPlayNetworkAddon} hands them to
 * {@code MinecraftServer#execute}, which the stun and Seeker payload guards wrap), so no extra hand-off is needed.
 * 服务端权威的炮筒发射。客户端只表达意图与朝向；本服务复核射手、手持且已装填的炮筒、冷却、眩晕/遥控锁定与
 * SparkTraits 武器封锁后才生成炮弹。Fabric 在服务端线程上执行游戏数据包接收器（{@code ServerPlayNetworkAddon} 把它们交给
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
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        ItemStack launcher = player.getMainHandStack();
        boolean holdingLauncher = launcher.getItem() instanceof PotionLauncherItem;
        PotionLauncherFireRules.Decision decision = PotionLauncherFireRules.decide(new PotionLauncherFireRules.Facts(
                game.isRunning(),
                GameFunctions.isPlayerPlayingAndAlive(player),
                PotionGunnerRules.isPotionGunner(game.getRole(player)),
                holdingLauncher,
                player.isSpectator(),
                ControlExpertStun.isStunned(player),
                SeekerRemoteSessionService.isLocked(player),
                holdingLauncher && SparkTraitsKillerBridge.blocksWeaponAction(player, launcher),
                holdingLauncher && player.getItemCooldownManager().isCoolingDown(launcher.getItem()),
                PotionLauncherLoad.isLoaded(launcher)));
        switch (decision) {
            case FIRE -> launch(player, launcher, payload);
            case NOT_LOADED -> dryClick(player);
            default -> {
            }
        }
    }

    /**
     * Spawns the shell first; only a spawned shell clears the load, starts the anti-double-click cooldown, plays the
     * loud launch sound to everyone nearby (counterplay information), and records one replay line. A failed launch
     * keeps the shell loaded. The cooldown is a plain vanilla write: it is the gunner's own launcher.
     * 先生成炮弹；只有成功生成才清除装填、开始防连点冷却、向附近所有人播放响亮的发射声（给对手的反制信息），并记录一条回放。
     * 发射失败则保留已装填的炮弹。冷却直接用原版写入：这是药炮手自己的炮筒。
     */
    private static void launch(ServerPlayerEntity player, ItemStack launcher, FirePotionLauncherC2SPacket payload) {
        PotionShellType type = PotionLauncherLoad.loaded(launcher).orElse(null);
        if (type == null) {
            return;
        }
        PotionLauncherFireRules.Aim aim = PotionLauncherFireRules.aim(payload.hasAim(), payload.yaw(),
                payload.pitch(), player.getYaw(), player.getPitch());
        if (!PotionShellEntity.launch(player, type, aim.yaw(), aim.pitch())) {
            return;
        }
        PotionLauncherLoad.setLoaded(launcher, null);
        player.getItemCooldownManager().set(launcher.getItem(), PotionGunnerRules.FIRE_COOLDOWN_TICKS);
        ServerWorld world = player.getServerWorld();
        world.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 3.0F, 0.55F);
        world.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.7F, 1.6F);
        GameRecordManager.recordItemUse(player, PotionGunnerRules.FIRE_REPLAY_ID, null,
                PotionGunnerReplay.fireData(type));
    }

    /** Empty launcher: a dry click only the shooter hears, plus an action-bar hint. / 未装填：仅射手可闻的空响与动作栏提示。 */
    private static void dryClick(ServerPlayerEntity player) {
        player.playSoundToPlayer(SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.8F, 1.2F);
        player.sendMessage(Text.translatable(NOT_LOADED_KEY).withColor(PotionGunnerRules.COLOR), true);
    }
}
