package dev.caecorthus.sparkwitch.component;

import dev.caecorthus.sparkwitch.SparkWitch;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithReturnPoint;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

/**
 * Owns canonical SparkWitch Wraith state and the private round-start return point.
 * 持有 SparkWitch 冤魂标准状态，以及私有的开局返回点。
 */
public final class WraithPlayerComponent implements AutoSyncedComponent {
    public static final ComponentKey<WraithPlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("wraith_player"),
            WraithPlayerComponent.class
    );

    private static final String RETURN_POINT_KEY = "ReturnPoint";

    private final PlayerEntity player;
    private final WraithPlayerState state = new WraithPlayerState();
    private @Nullable WraithReturnPoint returnPoint;

    public WraithPlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public boolean isActive() {
        return state.isActive();
    }

    public boolean isRestricted() {
        return state.isRestricted();
    }

    public boolean isPromoted() {
        return state.isPromoted();
    }

    public int getCompletedTasks() {
        return state.getCompletedTasks();
    }

    public @Nullable WraithState.Alignment getAlignment() {
        return state.getAlignment();
    }

    public boolean isPromotionPending() {
        return state.isPromotionPending();
    }

    public @Nullable WraithReturnPoint getReturnPoint() {
        return returnPoint;
    }

    public void captureReturnPoint(ServerPlayerEntity player) {
        returnPoint = new WraithReturnPoint(
                player.getServerWorld().getRegistryKey(),
                player.getPos(),
                player.getYaw(),
                player.getPitch()
        );
    }

    public void activate(WraithState.Alignment alignment) {
        state.activate(alignment);
        sync();
    }

    public int recordTaskCompletion() {
        int completedTasks = state.recordTaskCompletion();
        if (state.isActive()) {
            sync();
        }
        return completedTasks;
    }

    public void setPromotionPending(boolean pending) {
        if (state.setPromotionPending(pending)) {
            sync();
        }
    }

    public void promote() {
        if (state.promote()) {
            sync();
        }
    }

    public void clear() {
        boolean publicStateChanged = state.clear();
        returnPoint = null;
        if (publicStateChanged) {
            sync();
        }
    }

    public void sync() {
        KEY.sync(player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return true;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        boolean owner = recipient == player;
        // Only active/restricted are public; progress and alignment remain owner-private.
        // 只有 active/restricted 对外公开；进度与阵营仅同步给本人。
        buf.writeBoolean(state.isActive());
        buf.writeBoolean(state.isRestricted());
        buf.writeVarInt(owner ? state.getCompletedTasks() : 0);
        buf.writeVarInt(owner && state.getAlignment() != null ? state.getAlignment().ordinal() : -1);
        buf.writeBoolean(owner && state.isPromotionPending());
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        boolean active = buf.readBoolean();
        boolean restricted = buf.readBoolean();
        int completedTasks = buf.readVarInt();
        int alignmentOrdinal = buf.readVarInt();
        boolean promotionPending = buf.readBoolean();
        WraithState.Alignment alignment = active
                && alignmentOrdinal >= 0
                && alignmentOrdinal < WraithState.Alignment.values().length
                ? WraithState.Alignment.values()[alignmentOrdinal]
                : null;
        state.restore(active, restricted, completedTasks, alignment, promotionPending);
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (state.isActive()) {
            tag.putBoolean("WraithActive", true);
            tag.putBoolean("WraithRestricted", state.isRestricted());
            tag.putInt("WraithCompletedTasks", state.getCompletedTasks());
            tag.putString("WraithAlignment", state.getAlignment().name());
            if (state.isPromotionPending()) {
                tag.putBoolean("WraithPromotionPending", true);
            }
        }
        if (returnPoint != null) {
            NbtCompound point = new NbtCompound();
            point.putString("World", returnPoint.worldKey().getValue().toString());
            point.putDouble("X", returnPoint.position().getX());
            point.putDouble("Y", returnPoint.position().getY());
            point.putDouble("Z", returnPoint.position().getZ());
            point.putFloat("Yaw", returnPoint.yaw());
            point.putFloat("Pitch", returnPoint.pitch());
            tag.put(RETURN_POINT_KEY, point);
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        boolean active = tag.getBoolean("WraithActive");
        WraithState.Alignment alignment = active
                ? WraithState.Alignment.fromSerializedName(tag.getString("WraithAlignment"))
                : null;
        state.restore(
                active && alignment != null,
                tag.getBoolean("WraithRestricted"),
                tag.getInt("WraithCompletedTasks"),
                alignment,
                tag.getBoolean("WraithPromotionPending")
        );
        returnPoint = readReturnPoint(tag);
    }

    private static @Nullable WraithReturnPoint readReturnPoint(NbtCompound tag) {
        if (!tag.contains(RETURN_POINT_KEY, NbtElement.COMPOUND_TYPE)) {
            return null;
        }
        NbtCompound point = tag.getCompound(RETURN_POINT_KEY);
        Identifier worldId = Identifier.tryParse(point.getString("World"));
        if (worldId == null) {
            return null;
        }
        RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, worldId);
        return new WraithReturnPoint(
                worldKey,
                new Vec3d(point.getDouble("X"), point.getDouble("Y"), point.getDouble("Z")),
                point.getFloat("Yaw"),
                point.getFloat("Pitch")
        );
    }
}
