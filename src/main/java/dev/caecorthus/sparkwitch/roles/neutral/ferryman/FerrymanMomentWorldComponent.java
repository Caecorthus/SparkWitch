package dev.caecorthus.sparkwitch.roles.neutral.ferryman;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.Component;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The Soul Moment's round-scoped state ({@code sparkwitch:ferryman_moment}): whether it is running, its owner and the
 * Lost Souls it revived. Server only and transient; the clients learn who the souls are from their synced roles and the
 * GLOWING effect the moment applies.
 * 亡魂时刻的本局状态（{@code sparkwitch:ferryman_moment}）：是否进行中、拥有者与它复活的迷失灵魂。仅服务端且为瞬时状态；
 * 客户端通过已同步的职业与时刻施加的发光效果得知谁是迷失的灵魂。
 */
public final class FerrymanMomentWorldComponent implements Component {
    public static final ComponentKey<FerrymanMomentWorldComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("ferryman_moment"),
            FerrymanMomentWorldComponent.class
    );

    private boolean active;
    private @Nullable UUID owner;
    private final Set<UUID> souls = new LinkedHashSet<>();

    public FerrymanMomentWorldComponent(World world) {
    }

    public static FerrymanMomentWorldComponent get(World world) {
        return KEY.get(world);
    }

    public boolean isActive() {
        return active;
    }

    public @Nullable UUID owner() {
        return owner;
    }

    public Set<UUID> souls() {
        return Set.copyOf(souls);
    }

    public int soulCount() {
        return souls.size();
    }

    public boolean owns(UUID player) {
        return active && player.equals(owner);
    }

    public boolean isSoul(UUID player) {
        return active && souls.contains(player);
    }

    public void start(UUID ownerId, Set<UUID> revived) {
        active = true;
        owner = ownerId;
        souls.clear();
        souls.addAll(revived);
    }

    public void removeSoul(UUID soul) {
        souls.remove(soul);
    }

    public void clear() {
        active = false;
        owner = null;
        souls.clear();
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        // Transient by design: the round resets the moment with it. / 刻意保持瞬时：换局时时刻随之重置。
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        clear();
    }
}
