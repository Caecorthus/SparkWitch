package dev.caecorthus.sparkwitch.roles.neutral.ferryman;

import dev.caecorthus.sparkwitch.SparkWitch;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.util.UUID;

/**
 * The 10 s feign death a marked attacker falls into ({@code sparkwitch:ferryman_feign}): the fake body, the anchor it
 * was laid at with the facing to restore, the saved game mode and the previous invulnerability flag. Server only and
 * transient — a round reset or a respawn copy never carries it, and no client is told anything.
 * 被标记者陷入的 10 秒假死（{@code sparkwitch:ferryman_feign}）：假尸体、铺设它的锚点及需恢复的朝向、保存的游戏模式与
 * 先前的无敌标记。仅服务端且为瞬时状态——换局或重生不会带走它，也不会告诉任何客户端。
 */
public final class FerrymanFeignComponent implements org.ladysnake.cca.api.v3.component.Component {
    public static final ComponentKey<FerrymanFeignComponent> KEY = ComponentRegistry.getOrCreate(
            SparkWitch.id("ferryman_feign"),
            FerrymanFeignComponent.class
    );

    private final PlayerEntity player;
    private boolean feigning;
    private long untilTick;
    private @Nullable UUID bodyId;
    private double anchorX;
    private double anchorY;
    private double anchorZ;
    private float anchorYaw;
    private float anchorPitch;
    private GameMode previousMode = GameMode.SURVIVAL;
    private boolean wasInvulnerable;

    public FerrymanFeignComponent(PlayerEntity player) {
        this.player = player;
    }

    public static FerrymanFeignComponent get(PlayerEntity player) {
        return KEY.get(player);
    }

    public boolean isFeigning() {
        return feigning;
    }

    public long untilTick() {
        return untilTick;
    }

    public @Nullable UUID bodyId() {
        return bodyId;
    }

    public double anchorX() {
        return anchorX;
    }

    public double anchorY() {
        return anchorY;
    }

    public double anchorZ() {
        return anchorZ;
    }

    public float anchorYaw() {
        return anchorYaw;
    }

    public float anchorPitch() {
        return anchorPitch;
    }

    public GameMode previousMode() {
        return previousMode;
    }

    public boolean wasInvulnerable() {
        return wasInvulnerable;
    }

    public void start(long until, @Nullable UUID body, double x, double y, double z, float yaw, float pitch,
                      GameMode mode, boolean invulnerable) {
        feigning = true;
        untilTick = until;
        bodyId = body;
        anchorX = x;
        anchorY = y;
        anchorZ = z;
        anchorYaw = yaw;
        anchorPitch = pitch;
        previousMode = mode;
        wasInvulnerable = invulnerable;
    }

    public void clear() {
        feigning = false;
        untilTick = 0L;
        bodyId = null;
        anchorX = 0.0;
        anchorY = 0.0;
        anchorZ = 0.0;
        anchorYaw = 0.0F;
        anchorPitch = 0.0F;
        previousMode = GameMode.SURVIVAL;
        wasInvulnerable = false;
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        // Transient by design. / 刻意保持瞬时。
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        clear();
    }

    @Override
    public String toString() {
        return "FerrymanFeignComponent{" + player.getUuid() + "}";
    }
}
