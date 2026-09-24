package dev.caecorthus.sparkwitch.roles.killer.kidnapper;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.record.GameRecordManager;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
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
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 迷药的“被劫持”状态挂在目标玩家身上。
 *
 * <p>这样客户端只需要读取自己的组件就能黑屏、禁用攻击/使用/切换快捷栏；
 * 服务端 tick 也始终从目标出发，把目标拉回控制者身边。</p>
 */
public final class KidnapperControlComponent implements AutoSyncedComponent, ServerTickingComponent {
    public static final ComponentKey<KidnapperControlComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("kidnapper_control"),
            KidnapperControlComponent.class
    );

    private final PlayerEntity player;
    private @Nullable UUID controllerUuid;
    private int controlTicks;
    private int knockoutDrugStartCooldownTicks;

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
        if (controlTicks <= 0 || connectWithController()) {
            return;
        }

        teleportToController();
        notifyControllerRemainingTime();
        controlTicks--;
        if (controlTicks <= 0) {
            endControl(false);
            return;
        }
        sync();
    }

    public boolean isControlled() {
        return controlTicks > 0 && controllerUuid != null;
    }

    public int getControlTicks() {
        return Math.max(0, controlTicks);
    }

    public @Nullable UUID getControllerUuid() {
        return controllerUuid;
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

    public boolean isUsingStartCooldown(Item item) {
        return item == SparkWitchItems.knockoutDrug() && knockoutDrugStartCooldownTicks > 0;
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
    private boolean connectWithController() {
        PlayerEntity controller = controller();
        if (controller == null) {
            endControl(false);
            return true;
        }
        if (player.distanceTo(controller) > KidnapperRules.CONTROL_BREAK_DISTANCE) {
            releaseControlTip();
            endControl(false);
            return true;
        }
        if (controller.isSneaking()
                && GameFunctions.isPlayerAliveAndSurvival(controller)
                && GameFunctions.isPlayerAliveAndSurvival(player)) {
            releaseControlTip();
            endControl(true);
            return true;
        }
        if (GameFunctions.isPlayerSpectatingOrCreative(controller)
                || GameFunctions.isPlayerSpectatingOrCreative(player)) {
            releaseControlTip();
            endControl(false);
            return true;
        }
        return false;
    }

    private void teleportToController() {
        PlayerEntity controller = controller();
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

    private void notifyControllerRemainingTime() {
        PlayerEntity controller = controller();
        if (controller == null) {
            return;
        }
        controller.sendMessage(
                Text.translatable("message.sparkwitch.kidnapper.timeleft", controlTicks / 20)
                        .withColor(KidnapperRules.COLOR),
                true
        );
        if (controlTicks == 1) {
            releaseControlTip();
        }
    }

    private void releaseControlTip() {
        PlayerEntity controller = controller();
        if (controller == null) {
            return;
        }
        controller.sendMessage(
                Text.translatable("message.sparkwitch.kidnapper.release").withColor(KidnapperRules.COLOR),
                true
        );
        controller.playSoundToPlayer(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1.0F, 1.0F);
    }

    private void endControl(boolean manualRelease) {
        PlayerEntity controller = controller();
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
}
