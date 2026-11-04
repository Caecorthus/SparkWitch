package dev.caecorthus.sparkwitch.roles.killer.dominator;

import dev.caecorthus.sparkwitch.registry.SparkWitchDataComponents;
import dev.doctor4t.wathe.index.WatheItems;
import java.util.Optional;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * Server-only forced-shot sequence on the marked player: stash the held stack, issue a marked revolver in the same
 * hotbar slot, keep the player protected for the 1 s sequence, then confiscate the revolver and restore the stack.
 * 被标记者身上的仅服务端强制开枪序列：收起手持物品，在同一快捷栏槽位发一把带标记的左轮，1 秒序列期间保护该玩家，
 * 之后没收左轮并还原原物品。
 */
public final class DominatorForcedShotComponent implements ServerTickingComponent {
    public static final int DURATION_TICKS = 20;
    // A small orbit around the controlled player's body centre, kept under half a block even with the vertical sway.
    // 环绕被控制玩家身体中心的小粒子圈，加上垂直浮动后距离仍不超过半米。
    private static final double ORBIT_RADIUS = 0.45D;
    private static final double ORBIT_VERTICAL_SPREAD = 0.15D;
    private static final int ORBIT_PARTICLES_PER_TICK = 3;
    public static final ComponentKey<DominatorForcedShotComponent> KEY = ComponentRegistry.getOrCreate(
            DominatorRules.FORCED_SHOT_ID,
            DominatorForcedShotComponent.class
    );
    private static final String ACTIVE = "Active";
    private static final String SLOT = "Slot";
    private static final String REMAINING_TICKS = "RemainingTicks";
    private static final String ORIGINAL_STACK = "OriginalStack";

    private final PlayerEntity player;
    private boolean active;
    private int slot = -1;
    private ItemStack originalStack = ItemStack.EMPTY;
    private int remainingTicks;

    public DominatorForcedShotComponent(PlayerEntity player) {
        this.player = player;
    }

    public static boolean isProtectedPlayer(@Nullable PlayerEntity player) {
        return player != null
                && KEY.maybeGet(player)
                .map(component -> component.active && component.remainingTicks > 0)
                .orElse(false);
    }

    /**
     * Records the selected hotbar slot, removes its stack, issues the marked revolver and starts the 1 s protection.
     * Returns false when another sequence is running or no hotbar slot can be resolved.
     * 记录当前快捷栏槽位、移走其物品、发带标记左轮并开始 1 秒保护。已有序列或无法解析快捷栏槽位时返回 false。
     */
    public boolean begin() {
        if (active || !(player instanceof ServerPlayerEntity owner)) {
            return false;
        }
        PlayerInventory inventory = owner.getInventory();
        int selected = inventory.selectedSlot;
        if (!PlayerInventory.isValidHotbarIndex(selected)) {
            return false;
        }
        slot = selected;
        originalStack = inventory.getStack(selected).copy();
        ItemStack gun = new ItemStack(WatheItems.REVOLVER);
        gun.set(SparkWitchDataComponents.FORCED_SHOT_GUN, true);
        inventory.setStack(selected, gun);
        inventory.markDirty();
        owner.currentScreenHandler.syncState();
        owner.getItemCooldownManager().remove(WatheItems.REVOLVER);
        active = true;
        remainingTicks = DURATION_TICKS;
        ServerPlayNetworking.send(owner, new DominatorForcedShotS2CPacket(DURATION_TICKS));
        return true;
    }

    /** Server only: ends the sequence now, restoring the original stack. / 仅服务端：立即结束序列并还原原物品。 */
    public void restoreNow() {
        finish();
    }

    @Override
    public void serverTick() {
        if (!active) {
            return;
        }
        spawnOrbitParticles();
        if (remainingTicks > 0) {
            remainingTicks--;
        }
        if (remainingTicks > 0) {
            return;
        }
        finish();
    }

    /**
     * Ominous trial-spawner particles orbiting the controlled player during the sequence, staying within half a
     * block of the player's body centre.
     * 序列期间环绕被控制玩家的凶兆试炼刷怪粒子，始终位于其身体中心半米以内。
     */
    private void spawnOrbitParticles() {
        if (!(player.getWorld() instanceof ServerWorld world)) {
            return;
        }
        double centerY = player.getBodyY(0.5D);
        double baseAngle = player.age * 0.35D;
        for (int index = 0; index < ORBIT_PARTICLES_PER_TICK; index++) {
            double angle = baseAngle + index * (Math.PI * 2.0D / ORBIT_PARTICLES_PER_TICK);
            double y = centerY
                    + Math.sin(baseAngle * 2.0D + index) * ORBIT_VERTICAL_SPREAD;
            world.spawnParticles(
                    ParticleTypes.TRIAL_SPAWNER_DETECTION_OMINOUS,
                    player.getX() + Math.cos(angle) * ORBIT_RADIUS,
                    y,
                    player.getZ() + Math.sin(angle) * ORBIT_RADIUS,
                    1,
                    0.0D,
                    0.0D,
                    0.0D,
                    0.0D
            );
        }
    }

    private void finish() {
        if (!active) {
            return;
        }
        active = false;
        remainingTicks = 0;
        if (player instanceof ServerPlayerEntity owner) {
            owner.getItemCooldownManager().remove(WatheItems.REVOLVER);
            confiscateMarkedGuns(owner);
            restoreOriginal(owner);
        }
        originalStack = ItemStack.EMPTY;
        slot = -1;
    }

    private void restoreOriginal(ServerPlayerEntity owner) {
        PlayerInventory inventory = owner.getInventory();
        if (!originalStack.isEmpty()) {
            if (slot >= 0 && slot < inventory.size() && inventory.getStack(slot).isEmpty()) {
                inventory.setStack(slot, originalStack);
            } else if (!owner.giveItemStack(originalStack)) {
                owner.dropItem(originalStack, false, false);
            }
        }
        inventory.markDirty();
        owner.currentScreenHandler.syncState();
    }

    /**
     * Removes every stack carrying the forced-shot marker from online inventories, cursors and dropped items. Only
     * the temporarily issued revolver carries it, so a player's own guns are never touched.
     * 从在线背包、光标与掉落物中移除所有带强制开枪标记的物品堆。只有临时发放的左轮带此标记，玩家自己的枪不会被误删。
     */
    private static void confiscateMarkedGuns(ServerPlayerEntity owner) {
        MinecraftServer server = owner.getServer();
        if (server == null) {
            return;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PlayerInventory inventory = player.getInventory();
            boolean changed = false;
            for (int index = 0; index < inventory.size(); index++) {
                if (isForcedShotGun(inventory.getStack(index))) {
                    inventory.setStack(index, ItemStack.EMPTY);
                    changed = true;
                }
            }
            if (isForcedShotGun(player.currentScreenHandler.getCursorStack())) {
                player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
                changed = true;
            }
            if (changed) {
                inventory.markDirty();
                player.currentScreenHandler.syncState();
            }
        }
        for (ServerWorld world : server.getWorlds()) {
            for (ItemEntity item : world.getEntitiesByType(
                    EntityType.ITEM,
                    entity -> isForcedShotGun(entity.getStack())
            )) {
                item.discard();
            }
        }
    }

    private static boolean isForcedShotGun(ItemStack stack) {
        return stack != null
                && !stack.isEmpty()
                && Boolean.TRUE.equals(stack.get(SparkWitchDataComponents.FORCED_SHOT_GUN));
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        tag.putBoolean(ACTIVE, active);
        tag.putInt(REMAINING_TICKS, remainingTicks);
        tag.putInt(SLOT, slot);
        if (!originalStack.isEmpty()) {
            tag.put(ORIGINAL_STACK, originalStack.encode(registryLookup));
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        active = tag.getBoolean(ACTIVE);
        remainingTicks = Math.max(0, tag.getInt(REMAINING_TICKS));
        slot = tag.getInt(SLOT);
        originalStack = tag.contains(ORIGINAL_STACK, NbtElement.COMPOUND_TYPE)
                ? decode(registryLookup, tag.get(ORIGINAL_STACK)).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
        if (!active || remainingTicks <= 0 || !PlayerInventory.isValidHotbarIndex(slot)) {
            active = false;
            remainingTicks = 0;
            slot = -1;
            originalStack = ItemStack.EMPTY;
        }
    }

    private static Optional<ItemStack> decode(
            RegistryWrapper.WrapperLookup lookup,
            @Nullable NbtElement element
    ) {
        if (element == null) {
            return Optional.empty();
        }
        try {
            return ItemStack.fromNbt(lookup, element).filter(stack -> !stack.isEmpty());
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}
