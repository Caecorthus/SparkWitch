package dev.caecorthus.sparkwitch.client.planner;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.planner.PlannerRules;
import dev.caecorthus.sparkwitch.roles.civilian.planner.net.PlannerRecommendC2SPayload;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 规划大师 skill key (default G): marks the player under the crosshair and recommends them a task.
 * 规划大师技能键（默认 G）：标记准星下的玩家并为其推荐一项任务。
 */
public final class PlannerClientModule {
    private static final String TRANSLATION_KEY = "key.sparkwitch.planner_recommend";
    private static final String CATEGORY = "category.wathe.keybinds";
    private static final double REACH = 32.0D;
    private static KeyBinding keyBinding;
    private static long lastCastMillis = 0L;
    /**
     * The type the server last announced as recommended. Kept until the server says it expired, so a later task sync
     * (another task refreshing, a reroll) cannot silently downgrade it to an ordinary task.
     * 服务端最近公布的推荐类型。在服务端宣告失效之前一直保留，避免之后的任意一次任务同步
     * （其他任务刷新、重排）把它悄悄降级成普通任务。
     */
    private static dev.doctor4t.wathe.cca.PlayerMoodComponent.Task activeRecommended = null;
    /** True until the opening cooldown has been served once. 开局那次长冷却是否还没走完。 */
    private static boolean openingCooldown = true;
    private static boolean roleStarted = false;

    private PlannerClientModule() {
    }

    public static void register() {
        // Wrap the freshly recommended task as soon as the server says so.
        // 服务端一通知，就立刻把推荐任务包装起来。
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                dev.caecorthus.sparkwitch.roles.civilian.planner.net.PlannerTaskAssignedS2CPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    var values = dev.doctor4t.wathe.cca.PlayerMoodComponent.Task.values();
                    if (payload.ordinal() < 0 || payload.ordinal() >= values.length) {
                        return;
                    }
                    activeRecommended = payload.ordinal() < 0 ? null : values[payload.ordinal()];
                    reapplyRecommendation(context.client());
                }));
        // Uses the shared Wathe ability key; no SparkWitch-only keybind entry.
        // 复用列车体系共享技能控制键，不新增独立按键项。
    }

    /** Called when the shared ability key is pressed while the local player is the planner. */
    public static void onAbilityKey(net.minecraft.client.MinecraftClient client) {
        if (client.player == null || client.world == null || !client.player.isAlive()) {
            return;
        }
        if (!isReady()) {
            return;
        }
        // No target: recommend to yourself. 没有瞄准目标时，给自己推荐。
        PlayerEntity target = crosshairPlayer(client);
        lastCastMillis = System.currentTimeMillis();
        ClientPlayNetworking.send(new PlannerRecommendC2SPayload(
                target != null ? target.getUuid() : client.player.getUuid()));
    }

    /** Wraps the announced type as a recommendation; safe to call before the task sync arrives. 把已公布的类型包装成推荐任务；任务同步还没到也能安全调用。 */
    static void reapplyRecommendation(MinecraftClient client) {
        if (activeRecommended == null || client.player == null) {
            return;
        }
        var mood = dev.doctor4t.wathe.cca.PlayerMoodComponent.KEY.get(client.player);
        var existing = mood.tasks.get(activeRecommended);
        if (existing == null) {
            // Not present right now (finished, expired, or the sync has not landed): keep the marker for later.
            // 当前不存在（已完成、已过期，或同步还没到）：保留标记，稍后再试。
            return;
        }
        if (!(existing instanceof dev.caecorthus.sparkwitch.roles.civilian.planner.PlannerRecommendTask)) {
            mood.tasks.put(activeRecommended,
                    new dev.caecorthus.sparkwitch.roles.civilian.planner.PlannerRecommendTask(activeRecommended));
        }
    }

    public static void tick(MinecraftClient client) {
        // Deliberately does NOT touch mood.tasks any more: overwriting the client-side dummy task every tick made the
        // row flip between two identities and stalled completion. The server owns the real task; the client only
        // recolours/retexts the row in PlannerTaskColorMixin.
        // 不再改动 mood.tasks：每 tick 覆盖客户端的占位任务会让这一行来回跳变并卡住完成判定。
        // 真实任务由服务端持有，客户端只在 PlannerTaskColorMixin 里改颜色与文本。
        // Key handling moved to the shared ability dispatch in SparkWitchClient.
        // 按键处理已移到 SparkWitchClient 的共享技能分发。
    }

    /** Starts the 45 s opening cooldown the server applies on assignment. 与服务端一致的开局 45 秒冷却。 */
    public static void tickRoleState(boolean hasPlannerRole) {
        if (!hasPlannerRole) {
            roleStarted = false;
            lastCastMillis = 0L;
            return;
        }
        if (!roleStarted) {
            roleStarted = true;
            lastCastMillis = System.currentTimeMillis();
        }
    }

    public static boolean isReady() {
        return remainingCooldownSeconds() <= 0;
    }

    /** HUD countdown; the first cast window is the long opening cooldown. HUD 倒计时；首次使用按开局长冷却计算。 */
    public static int remainingCooldownSeconds() {
        if (lastCastMillis == 0L) {
            return 0;
        }
        long elapsed = System.currentTimeMillis() - lastCastMillis;
        long total = (openingCooldown
                ? PlannerRules.INITIAL_COOLDOWN_TICKS
                : PlannerRules.COOLDOWN_TICKS) * 50L;
        if (elapsed >= total) {
            return 0;
        }
        return (int) Math.ceil((total - elapsed) / 1000.0D);
    }

    /** Name of the player currently under the crosshair, or null when nothing is targeted. 准星下玩家的名字，无目标时为 null。 */
    public static @Nullable Text targetName() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return null;
        }
        PlayerEntity target = crosshairPlayer(client);
        return target == null ? null : target.getDisplayName();
    }

    public static Text keyText() {
        return dev.caecorthus.sparkwitch.client.hooks.WitchAbilityKeyBridge.keyText();
    }

    /** Raycast for a player within reach; null when the crosshair is not on one. 准星射线检测玩家。 */
    private static @Nullable PlayerEntity crosshairPlayer(MinecraftClient client) {
        PlayerEntity player = client.player;
        Vec3d start = player.getCameraPosVec(1.0F);
        Vec3d direction = player.getRotationVec(1.0F);
        Vec3d end = start.add(direction.multiply(REACH));
        Box box = player.getBoundingBox().stretch(direction.multiply(REACH)).expand(1.0D);
        EntityHitResult hit = ProjectileUtil.raycast(player, start, end, box,
                (Entity entity) -> entity instanceof PlayerEntity && entity != player && entity.isAlive(),
                REACH * REACH);
        if (hit != null && hit.getEntity() instanceof PlayerEntity hitPlayer) {
            return hitPlayer;
        }
        return null;
    }

    public static void reset() {
        if (keyBinding != null) {
            keyBinding.setPressed(false);
            while (keyBinding.wasPressed()) {
                // drain stale presses
            }
        }
    }
}
