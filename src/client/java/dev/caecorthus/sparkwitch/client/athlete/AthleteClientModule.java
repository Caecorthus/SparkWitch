package dev.caecorthus.sparkwitch.client.athlete;

import dev.caecorthus.sparkwitch.SparkWitchRoles;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityHandler;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityRegistry;
import dev.caecorthus.sparkwitch.roles.civilian.athlete.AthleteRules;
import dev.caecorthus.sparkwitch.roles.civilian.athlete.net.AthleteDashC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

/**
 * Binds 运动员's dash to SparkWitch's shared secondary ability key.
 * 把运动员的冲刺绑定到 SparkWitch 通用副技能键。
 */
public final class AthleteClientModule {
    private static boolean registered;
    /** Client-side mirror of the server cooldown, used only for the HUD countdown. 客户端冷却镜像，仅用于 HUD 倒计时。 */
    private static long lastCastMillis = 0L;

    private AthleteClientModule() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        // Uses the shared Wathe ability key; no SparkWitch-only keybind entry.
        // 复用列车体系共享技能控制键，不新增独立按键项。
        SecondaryAbilityRegistry.register(SparkWitchRoles.ATHLETE_ID, new SecondaryAbilityHandler() {
            @Override
            public void onPressed(MinecraftClient client) {
                if (client.player != null && remainingCooldownSeconds() <= 0) {
                    lastCastMillis = System.currentTimeMillis();
                    ClientPlayNetworking.send(new AthleteDashC2SPayload());
                }
            }
        });
        registered = true;
    }

    /**
     * Called every client tick with whether the local player currently has the role. The first tick of the role starts
     * the same 15 s cooldown the server applies on assignment, so the HUD matches the server clock.
     * 每个客户端 tick 调用；身份首次生效时启动与服务端一致的 15 秒开局冷却，使 HUD 与服务端同步。
     */
    /** Called when the shared ability key is pressed while the local player is the Athlete. */
    public static void onAbilityKey(net.minecraft.client.MinecraftClient client) {
        if (client.player == null || !client.player.isAlive()) {
            return;
        }
        if (!isReady()) {
            return;
        }
        lastCastMillis = System.currentTimeMillis();
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new AthleteDashC2SPayload());
    }

    public static void tickRoleState(boolean hasAthleteRole) {
        if (!hasAthleteRole) {
            lastCastMillis = 0L;
            return;
        }
        if (lastCastMillis == 0L) {
            lastCastMillis = System.currentTimeMillis();
        }
    }

    /** Whether the dash is usable right now. 当前是否可以使用冲刺。 */
    public static boolean isReady() {
        return remainingCooldownSeconds() <= 0;
    }

    /** Records a cast so the HUD countdown restarts. 记录一次施放，重启 HUD 倒计时。 */
    public static void markCast() {
        lastCastMillis = System.currentTimeMillis();
    }

    /** Seconds left on the HUD countdown, 0 when ready. HUD 倒计时剩余秒数，可用时为 0。 */
    public static int remainingCooldownSeconds() {
        if (lastCastMillis == 0L) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - lastCastMillis;
        long total = AthleteRules.COOLDOWN_TICKS * 50L;
        if (elapsed >= total) {
            return 0;
        }
        return (int) Math.ceil((total - elapsed) / 1000.0D);
    }

    public static void reset() {
        lastCastMillis = 0L;
    }
}
