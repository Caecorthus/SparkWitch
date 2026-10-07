package dev.caecorthus.sparkwitch.roles.killer.magician;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/** 每 tick 的轨迹、姿态和临时背包快照。 */
public record MagicianReplayFrame(
        double x, double y, double z, float yaw, float pitch, float headYaw, float bodyYaw,
        double velocityX, double velocityY, double velocityZ,
        String poseName, boolean sneaking, boolean sprinting, boolean onGround, int selectedSlot,
        boolean sitting,
        List<ItemStack> mainStacks, List<ItemStack> armorStacks, List<ItemStack> offHandStacks,
        boolean usingItem, @Nullable Hand activeHand, int itemUseTimeLeft, @Nullable BlockPos sleepingPosition
) {
    public MagicianReplayFrame {
        selectedSlot = Math.max(0, Math.min(8, selectedSlot));
        mainStacks = copy(mainStacks); armorStacks = copy(armorStacks); offHandStacks = copy(offHandStacks);
        itemUseTimeLeft = Math.max(0, itemUseTimeLeft);
    }
    public static MagicianReplayFrame capture(PlayerEntity p) {
        PlayerInventory i = p.getInventory();
        return new MagicianReplayFrame(p.getX(), p.getY(), p.getZ(), p.getYaw(), p.getPitch(), p.getHeadYaw(), p.bodyYaw,
                p.getVelocity().x, p.getVelocity().y, p.getVelocity().z,
                p.getPose().name(),
                p.isSneaking(), p.isSprinting(), p.isOnGround(), i.selectedSlot, p.hasVehicle(), i.main, i.armor, i.offHand,
                p.isUsingItem(), p.isUsingItem() ? p.getActiveHand() : null, p.isUsingItem() ? p.getItemUseTimeLeft() : 0,
                p.getSleepingPosition().orElse(null));
    }
    public void applyTo(MagicianPlaybackEntity e) {
        e.clearEquipment(); e.refreshPositionAndAngles(x, y, z, yaw, pitch); e.setHeadYaw(headYaw); e.bodyYaw = bodyYaw;
        e.setSneaking(sneaking); e.setSprinting(sprinting); e.setOnGround(onGround); e.setReplaySitting(sitting);
        applySleepingState(e);
        e.setPose(parsePose(poseName));
        e.equipStack(EquipmentSlot.MAINHAND, stack(mainStacks, selectedSlot)); e.equipStack(EquipmentSlot.OFFHAND, stack(offHandStacks, 0));
        e.equipStack(EquipmentSlot.FEET, stack(armorStacks, 0)); e.equipStack(EquipmentSlot.LEGS, stack(armorStacks, 1));
        e.equipStack(EquipmentSlot.CHEST, stack(armorStacks, 2)); e.equipStack(EquipmentSlot.HEAD, stack(armorStacks, 3));
        e.setReplayUseState(usingItem, activeHand); e.setReplayItemUseTimeLeft(usingItem ? itemUseTimeLeft : 0);
    }
    public void applyInventoryTo(PlayerEntity p) {
        PlayerInventory i = p.getInventory(); apply(mainStacks, i.main); apply(armorStacks, i.armor); apply(offHandStacks, i.offHand); i.selectedSlot = selectedSlot;
        if (usingItem && activeHand != null && !p.getStackInHand(activeHand).isEmpty()) p.setCurrentHand(activeHand);
        else if (p.isUsingItem()) p.stopUsingItem();
        if (usingItem && p instanceof MagicianPlaybackFakePlayer fp) fp.setReplayItemUseTimeLeft(itemUseTimeLeft);
    }
    /**
     * 将同一帧的运动/姿态完整应用给服务端代理玩家。
     * 代理玩家是所有刀、枪、球棒、方块交互和投掷动作的实际执行者，
     * 如果只更新可见皮套而不更新代理，所有目标射线都会从错误坐标计算。
     */
    public void applyToProxy(MagicianPlaybackFakePlayer p) {
        applyInventoryTo(p);
        p.refreshPositionAndAngles(x, y, z, yaw, pitch);
        p.setHeadYaw(headYaw);
        p.bodyYaw = bodyYaw;
        // Wathe 的 GrenadeItem 会把投掷者当前速度并入投掷初速度；回放代理也必须继承录制帧速度。
        p.setVelocity(velocityX, velocityY, velocityZ);
        p.setSneaking(sneaking);
        p.setSprinting(sprinting);
        p.setOnGround(onGround);
        applySleepingState(p);
        p.setPose(parsePose(poseName));
    }
    public ItemStack getStackInHand(Hand hand) {
        return hand == Hand.MAIN_HAND ? stack(mainStacks, selectedSlot) : stack(offHandStacks, 0);
    }
    private void applySleepingState(LivingEntity p) {
        if (sleepingPosition != null) p.setSleepingPosition(sleepingPosition);
        else p.clearSleepingPosition();
    }
    private static EntityPose parsePose(String name) {
        if (name == null || name.isBlank()) return EntityPose.STANDING;
        try { return EntityPose.valueOf(name); }
        catch (IllegalArgumentException ignored) { return EntityPose.STANDING; }
    }
    private static List<ItemStack> copy(List<ItemStack> src) { List<ItemStack> out = new ArrayList<>(src.size()); for (ItemStack s:src) out.add(s.copy()); return List.copyOf(out); }
    private static ItemStack stack(List<ItemStack> src, int index) { return index >= 0 && index < src.size() ? src.get(index).copy() : ItemStack.EMPTY; }
    private static void apply(List<ItemStack> src, List<ItemStack> dst) { for (int i=0;i<dst.size();i++) dst.set(i, stack(src,i)); }
}
