package dev.caecorthus.sparkwitch.client.controlexpert;

import dev.caecorthus.sparkwitch.client.hooks.WitchAbilityKeyBridge;
import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStunRules;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.ScreenHandlerProvider;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import org.jetbrains.annotations.Nullable;

/**
 * Client prediction of the stun input lock; the server independently rejects every blocked action. Esc, F-keys,
 * voice-chat keys, mouse look and screen input stay usable. The owner's client unlocks only after the server's
 * zero sync, so it can never unlock early.
 * 眩晕输入锁的客户端预测；服务端会独立拒绝所有被阻止的行为。Esc、F 键、语音聊天按键、鼠标视角与界面输入
 * 仍可使用。拥有者客户端只在收到服务端的归零同步后才解锁，因此不会提前解锁。
 */
public final class ControlExpertStunClient {
    private static boolean registered;
    private static boolean wasStunned;

    private ControlExpertStunClient() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        // START_CLIENT_TICK runs before handleInputEvents in the same tick, unlike a CCA client tick.
        // START_CLIENT_TICK 在同一刻的 handleInputEvents 之前运行，CCA 客户端 tick 则太晚。
        ClientTickEvents.START_CLIENT_TICK.register(ControlExpertStunClient::tick);
    }

    public static boolean isLocalStunned() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client != null
                && SparkWitchServerConnection.isConfirmedServer()
                && client.player != null
                && ControlExpertStun.isStunned(client.player);
    }

    /** True when a key of {@code category} must read as released. / 该分类的按键需视为未按下时返回 true。 */
    public static boolean blocksKey(@Nullable String category) {
        return isLocalStunned() && !ControlExpertStunRules.isExemptKeyCategory(category);
    }

    /**
     * Backup for handleInputEvents: cancel item use without a release and stop mining every tick of the stun.
     * handleInputEvents 的兜底：在眩晕的每一刻取消物品使用（不触发松手）并停止挖掘。
     */
    public static void settleInput(MinecraftClient client) {
        if (!isLocalStunned()) {
            return;
        }
        ClientPlayerEntity player = client.player;
        if (player != null && player.isUsingItem()) {
            player.clearActiveItem();
        }
        if (client.interactionManager != null) {
            client.interactionManager.cancelBlockBreaking();
        }
    }

    enum Edge { NONE, START, END }

    static Edge edge(boolean wasStunned, boolean stunned) {
        if (stunned == wasStunned) {
            return Edge.NONE;
        }
        return stunned ? Edge.START : Edge.END;
    }

    /** Pause and option screens stay open; gameplay screens close. / 暂停与设置界面保留；游戏玩法界面关闭。 */
    static boolean keepsScreenOpen(@Nullable Class<?> screenClass) {
        return screenClass != null
                && (GameMenuScreen.class.isAssignableFrom(screenClass)
                || OptionsScreen.class.isAssignableFrom(screenClass)
                || GameOptionsScreen.class.isAssignableFrom(screenClass));
    }

    private static void tick(MinecraftClient client) {
        boolean stunned = isLocalStunned();
        Edge transition = edge(wasStunned, stunned);
        wasStunned = stunned;
        if (transition == Edge.START) {
            onStunStart(client);
        } else if (transition == Edge.END) {
            // Presses made during the stun must not replay afterwards. / 眩晕期间的按键不得在结束后重放。
            drainQueuedPresses(client);
        }
    }

    private static void onStunStart(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player != null && player.isUsingItem()) {
            player.clearActiveItem();
        }
        if (client.interactionManager != null) {
            client.interactionManager.cancelBlockBreaking();
        }
        closeGameplayScreen(client);
        WitchAbilityKeyBridge.reset();
        drainQueuedPresses(client);
    }

    private static void closeGameplayScreen(MinecraftClient client) {
        Screen screen = client.currentScreen;
        if (screen == null || keepsScreenOpen(screen.getClass())) {
            return;
        }
        // Wathe's inventory and shop are ScreenHandlerProviders but not vanilla HandledScreens.
        // Wathe 的背包与商店实现 ScreenHandlerProvider，但并非原版 HandledScreen。
        if (screen instanceof ScreenHandlerProvider<?> && client.player != null) {
            client.player.closeHandledScreen();
        } else {
            client.setScreen(null);
        }
    }

    /**
     * Zeroes queued presses through the duck interface. It never calls wasPressed in a loop (that would feed other
     * mods' return hooks) and never unpresses keys (that would drop toggle sneak/sprint).
     * 通过鸭子接口清零排队的按键次数。从不循环调用 wasPressed（会触发其他模组的返回钩子），
     * 也不强制松开按键（会丢失切换潜行/疾跑状态）。
     */
    static void drainQueuedPresses(MinecraftClient client) {
        if (client.options == null) {
            return;
        }
        for (KeyBinding key : client.options.allKeys) {
            if (!ControlExpertStunRules.isExemptKeyCategory(key.getCategory())) {
                ((ControlExpertKeyDrain) key).sparkwitch$discardQueuedPresses();
            }
        }
    }
}
