package dev.caecorthus.sparkwitch.client.consultant;

import dev.caecorthus.sparkwitch.roles.killer.consultant.net.ConsultantInspectC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * 顾问's inspection: aim at a player and press the shared ability key.
 * 顾问的查看：准星对准玩家，按下共享技能键。
 */
public final class ConsultantClientModule {
    private static final double REACH = dev.caecorthus.sparkwitch.roles.killer.consultant.ConsultantRules.REACH;

    private static long lastCastMillis = 0L;

    private ConsultantClientModule() {
    }

    /** Client-side wiring; the consultant needs no S2C receiver. 客户端接线；顾问不需要 S2C 接收器。 */
    public static void register() {
    }

    /** HUD countdown. HUD 倒计时。 */
    public static int remainingCooldownSeconds() {
        if (lastCastMillis == 0L) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - lastCastMillis;
        long total = dev.caecorthus.sparkwitch.roles.killer.consultant.ConsultantRules.COOLDOWN_TICKS * 50L;
        if (elapsed >= total) {
            return 0;
        }
        return (int) Math.ceil((total - elapsed) / 1000.0D);
    }

    public static boolean isReady() {
        return remainingCooldownSeconds() <= 0;
    }

    public static net.minecraft.text.Text keyText() {
        return dev.caecorthus.sparkwitch.client.hooks.WitchAbilityKeyBridge.keyText();
    }

    /** Name of the player under the crosshair, or null. 准星下玩家名，无目标为 null。 */
    public static @Nullable net.minecraft.text.Text targetName() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return null;
        }
        PlayerEntity target = crosshairPlayer(client);
        return target == null ? null : target.getDisplayName();
    }

    public static void onAbilityKey(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            return;
        }
        if (!isReady()) {
            return;
        }
        PlayerEntity target = crosshairPlayer(client);
        if (target != null) {
            lastCastMillis = System.currentTimeMillis();
            ClientPlayNetworking.send(new ConsultantInspectC2SPayload(target.getUuid()));
        }
    }

    private static @Nullable PlayerEntity crosshairPlayer(MinecraftClient client) {
        PlayerEntity player = client.player;
        Vec3d start = player.getCameraPosVec(1.0F);
        Vec3d direction = player.getRotationVec(1.0F);
        Vec3d end = start.add(direction.multiply(REACH));
        Box box = player.getBoundingBox().stretch(direction.multiply(REACH)).expand(1.0D);
        EntityHitResult hit = ProjectileUtil.raycast(player, start, end, box,
                (Entity entity) -> entity instanceof PlayerEntity && entity != player && entity.isAlive(),
                REACH * REACH);
        return hit != null && hit.getEntity() instanceof PlayerEntity target ? target : null;
    }
}
