package dev.caecorthus.sparkwitch.roles.witch.riftwalker.gate;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.Component;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

/**
 * Stable CCA contract {@code sparkwitch:rift_gates} (world, server-only): the per-world gate list stored in
 * {@link RiftGateRegistryState}. Never synced (clients learn gates only from entities and the console snapshot) and
 * never persisted (gates are unsaved entities, so a reload starts empty). Only {@link RiftGateRegistry} (P1) writes it.
 * 稳定 CCA 契约 {@code sparkwitch:rift_gates}（世界，仅服务端）：存于 {@link RiftGateRegistryState} 的每世界门列表。
 * 从不同步（客户端只从实体与控制台快照得知门）、从不持久化（门是不存盘实体，重载后为空）。只有 {@link RiftGateRegistry}（P1）写入。
 */
public final class RiftGateRegistryComponent extends RiftGateRegistryState implements Component {
    public static final ComponentKey<RiftGateRegistryComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("rift_gates"), RiftGateRegistryComponent.class);

    public RiftGateRegistryComponent(World world) {
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        clear();
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
    }
}
