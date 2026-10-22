package dev.caecorthus.sparkwitch.roles.killer.kidnapper;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.util.TickTime;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 迷药的“被劫持”状态挂在目标玩家身上。
 *
 * <p>这样客户端只需要读取自己的组件就能黑屏、禁用攻击/使用/切换快捷栏；
 * 服务端 tick 也始终从目标出发，把目标拉回控制者身边。</p>
 *
 * <p>While controlled the server syncs only when the HUD's shown second changes (and on start/end); the client counts
 * down in between without crossing that second, so the HUD changes exactly when the sync lands and the client's
 * {@link #isControlled()} still ends only with the server's end sync.
 * 被控制期间，服务端只在 HUD 显示的秒数变化时（以及开始/结束时）同步；客户端在两次同步之间自行倒数，但不会越过当前显示的秒数，
 * 因此 HUD 恰在同步到达时变化，客户端的 {@link #isControlled()} 仍只会因服务端的结束同步而结束。</p>
 */
public final class KidnapperControlComponent
        implements AutoSyncedComponent, ServerTickingComponent, ClientTickingComponent {
    public static final ComponentKey<KidnapperControlComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("kidnapper_control"),
            KidnapperControlComponent.class
    );

    private final PlayerEntity player;
    private @Nullable UUID controllerUuid;
    private int controlTicks;
    private int knockoutDrugStartCooldownTicks;
    /** Client only: the local countdown never goes below this (the shown second's first tick). / 仅客户端：本地倒数下限。 */
    private int clientFloorTicks;

    public KidnapperControlComponent(PlayerEntity player) {
        this.player = player;
    }

    @Override
    public void serverTick() {
        tickKnockoutDrugStartCooldown();
        if (controlTicks <= 0) {
            return;
        }

        resetWhenOutOfGame();
        if (controlTicks <= 0) {
            return;
        }
        // Resolved once per tick: the target's world does not change during this tick. / 每刻只解析一次控制者。
        PlayerEntity controller = controller();
        if (connectWithController(controller)) {
            return;
        }

        teleportToController(controller);
        notifyControllerRemainingTime(controller);
        controlTicks--;
        if (controlTicks <= 0) {
            endControl(controller, false);
            return;
        }
        if (Cadence.shownSecondChanged(controlTicks)) {
            sync();
        }
    }

    @Override
    public void clientTick() {
        if (controlTicks > clientFloorTicks) {
            controlTicks--;
        }
    }

    public boolean isControlled() {
        return controlTicks > 0 && controllerUuid != null;
    }

    public int getControlTicks() {
        return Math.max(0, controlTicks);
    }

    public boolean isControlledBy(PlayerEntity controller) {
        return controller != null && controller.getUuid().equals(controllerUuid) && controlTicks > 0;
    }

    public void startControl(PlayerEntity controller) {
        controllerUuid = controller.getUuid();
        controlTicks = KidnapperRules.KNOCKOUT_DRUG_CONTROL_TICKS;
        sync();
    }

    /**
     * 真实绑架者开局迷药拥有 40 秒初始冷却。
     * 这个来源标记只给客户端 tooltip/后续兼容判断使用，不参与真正的冷却结算。
     */
    public void startRoundCooldowns() {
        knockoutDrugStartCooldownTicks = KidnapperRules.KNOCKOUT_DRUG_START_COOLDOWN_TICKS;
        sync();
    }

    public void clearKnockoutDrugStartCooldown() {
        if (knockoutDrugStartCooldownTicks <= 0) {
            return;
        }
        knockoutDrugStartCooldownTicks = 0;
        sync();
    }

    public void reset() {
        resetControlState();
        sync();
    }

    /**
     * 回合重置、重新分配身份或玩家被清理时，才清掉开局冷却来源标记。
     * 普通释放劫持只清 controlTicks，避免误清目标自己身上的迷药冷却。
     */
    public void resetAll() {
        resetControlState();
        knockoutDrugStartCooldownTicks = 0;
        player.getItemCooldownManager().remove(SparkWitchItems.knockoutDrug());
        sync();
    }

    private void tickKnockoutDrugStartCooldown() {
        if (knockoutDrugStartCooldownTicks <= 0) {
            return;
        }
        knockoutDrugStartCooldownTicks--;
        if (knockoutDrugStartCooldownTicks == 0) {
            sync();
        }
    }

    private void resetWhenOutOfGame() {
        if (GameWorldComponent.KEY.get(player.getWorld()).getRole(player) == null) {
            resetAll();
        }
    }

    /**
     * 返回 true 表示本 tick 已经结束控制，后续不能再传送目标，避免“刚释放又被拉回”的一帧错位。
     */
    private boolean connectWithController(@Nullable PlayerEntity controller) {
        if (controller == null) {
            endControl(null, false);
            return true;
        }
        if (player.distanceTo(controller) > KidnapperRules.CONTROL_BREAK_DISTANCE) {
            releaseControlTip(controller);
            endControl(controller, false);
            return true;
        }
        if (controller.isSneaking()
                && GameFunctions.isPlayerAliveAndSurvival(controller)
                && GameFunctions.isPlayerAliveAndSurvival(player)) {
            releaseControlTip(controller);
            endControl(controller, true);
            return true;
        }
        if (GameFunctions.isPlayerSpectatingOrCreative(controller)
                || GameFunctions.isPlayerSpectatingOrCreative(player)) {
            releaseControlTip(controller);
            endControl(controller, false);
            return true;
        }
        return false;
    }

    private void teleportToController(@Nullable PlayerEntity controller) {
        if (controller == null || player.getWorld().isClient || !(player.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }
        player.teleport(
                serverWorld,
                controller.getX(),
                controller.getY(),
                controller.getZ(),
                Set.of(),
                controller.getYaw(),
                controller.getPitch()
        );
    }

    private void notifyControllerRemainingTime(@Nullable PlayerEntity controller) {
        if (controller == null) {
            return;
        }
        // Sent every tick on purpose: any other action-bar message to the controller is overwritten the next tick.
        // 有意每刻发送：发给控制者的其他动作栏消息会在下一刻被覆盖。
        controller.sendMessage(
                Text.translatable("message.sparkwitch.kidnapper.timeleft", controlTicks / 20)
                        .withColor(KidnapperRules.COLOR),
                true
        );
        if (controlTicks == 1) {
            releaseControlTip(controller);
        }
    }

    private void releaseControlTip(@Nullable PlayerEntity controller) {
        if (controller == null) {
            return;
        }
        controller.sendMessage(
                Text.translatable("message.sparkwitch.kidnapper.release").withColor(KidnapperRules.COLOR),
                true
        );
        controller.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0F, 1.0F);
    }

    private void endControl(@Nullable PlayerEntity controller, boolean manualRelease) {
        if (manualRelease) {
            // 主动潜行放人时，回放记录“谁提前结束了对谁的劫持”。
            if (controller instanceof ServerPlayerEntity serverController
                    && player instanceof ServerPlayerEntity serverTarget) {
                GameRecordManager.recordSkillUse(serverController, KidnapperRules.RELEASE_EVENT_ID, serverTarget, null);
            }
        } else if (player instanceof ServerPlayerEntity serverTarget) {
            // 自然结束时用目标作为 actor，表示“这个人的被劫持状态结束”。
            GameRecordManager.recordSkillUse(serverTarget, KidnapperRules.RELEASE_EVENT_ID, null, null);
        }
        reset();
    }

    private @Nullable PlayerEntity controller() {
        return controllerUuid == null ? null : player.getWorld().getPlayerByUuid(controllerUuid);
    }

    private void resetControlState() {
        controllerUuid = null;
        controlTicks = 0;
    }

    private void sync() {
        KEY.sync(player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        buf.writeBoolean(controllerUuid != null);
        if (controllerUuid != null) {
            buf.writeUuid(controllerUuid);
        }
        buf.writeVarInt(Math.max(0, controlTicks));
        buf.writeBoolean(knockoutDrugStartCooldownTicks > 0);
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        controllerUuid = buf.readBoolean() ? buf.readUuid() : null;
        controlTicks = Math.max(0, buf.readVarInt());
        clientFloorTicks = Cadence.clientFloor(controlTicks);
        knockoutDrugStartCooldownTicks = buf.readBoolean() ? 1 : 0;
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (controllerUuid != null) {
            tag.putUuid("Controller", controllerUuid);
        }
        if (controlTicks > 0) {
            tag.putInt("ControlTicks", controlTicks);
        }
        if (knockoutDrugStartCooldownTicks > 0) {
            tag.putInt("KnockoutDrugStartCooldownTicks", knockoutDrugStartCooldownTicks);
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        controllerUuid = tag.containsUuid("Controller") ? tag.getUuid("Controller") : null;
        controlTicks = tag.contains("ControlTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("ControlTicks"))
                : 0;
        knockoutDrugStartCooldownTicks = tag.contains("KnockoutDrugStartCooldownTicks", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("KnockoutDrugStartCooldownTicks"))
                : 0;
    }

    /**
     * Pure sync cadence of the controlled countdown, matching {@code KidnapperControlledHudRenderer}'s shown seconds
     * ({@code max(1, ceil(ticks / 20))}). Nested so tests reach it without loading the component key.
     * 被控制倒数的纯同步节奏，与 {@code KidnapperControlledHudRenderer} 显示的秒数（{@code max(1, ceil(ticks / 20))}）一致。
     * 作为嵌套类，测试无需加载组件键即可访问。
     */
    static final class Cadence {
        private Cadence() {
        }

        static int shownSeconds(int ticks) {
            // Same formula as KidnapperControlledHudRenderer. / 与 KidnapperControlledHudRenderer 相同的公式。
            return Math.max(1, TickTime.ceilSecondsRaw(ticks));
        }

        /** True when one tick down to {@code ticks} changes the shown second. / 倒数到该值时显示的秒数是否变化。 */
        static boolean shownSecondChanged(int ticks) {
            return shownSeconds(ticks) != shownSeconds(ticks + 1);
        }

        /**
         * Lowest local value that still shows the same second (at least 1 while controlled, 0 when not), so the client
         * never moves the HUD or ends control on its own.
         * 仍显示同一秒的最低本地值（被控制时至少为 1，未被控制时为 0），因此客户端不会自行改变 HUD 或结束控制。
         */
        static int clientFloor(int ticks) {
            return ticks <= 0 ? 0 : (shownSeconds(ticks) - 1) * 20 + 1;
        }
    }
}
