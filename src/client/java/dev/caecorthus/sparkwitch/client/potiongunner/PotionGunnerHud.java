package dev.caecorthus.sparkwitch.client.potiongunner;

import dev.caecorthus.sparkwitch.net.SparkWitchServerConnection;
import dev.caecorthus.sparkwitch.roles.witch.potiongunner.PotionShellType;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.client.WatheClient;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

import java.util.Optional;

/**
 * The loaded-shell line above the hotbar while the launcher is held and not scoped (the scope reticle shows the same
 * text inside the lens). It reads only the local player's own synced launcher stack, so it never leaks anything. Role-owned
 * presentation, never the witch skill inventory panel.
 * 手持炮筒且未开镜时，快捷栏上方的装填行（开镜时同样的文字显示在镜片内）。只读取本地玩家自己已同步的炮筒物品，
 * 因此不会泄露任何信息。职业自有展示，从不使用魔女技能背包面板。
 */
public final class PotionGunnerHud {
    private PotionGunnerHud() {
    }

    static void render(DrawContext context, RenderTickCounter tickCounter) {
        if (!SparkWitchServerConnection.isConfirmedServer()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return;
        }
        // HudRenderCallback still fires under F1, unlike Wathe's own HUD layer; follow Wathe's train HUD too.
        // HudRenderCallback 在 F1 下仍会触发，与 Wathe 自身 HUD 层不同；同时跟随 Wathe 列车 HUD 的显示状态。
        boolean trainHudActive = WatheClient.trainComponent != null && WatheClient.trainComponent.hasHud();
        boolean gameActive = GameWorldComponent.KEY.get(client.world).getGameStatus()
                == GameWorldComponent.GameStatus.ACTIVE;
        if (!PotionScopeRules.showsHudLine(client.options.hudHidden, trainHudActive, gameActive,
                GameFunctions.isPlayerPlayingAndAlive(player), PotionScopeClient.holdsLauncherInMainHand(player),
                PotionScopeClient.isScoped())) {
            return;
        }
        Optional<PotionShellType> loaded = PotionScopeClient.loaded(player.getMainHandStack());
        TextRenderer renderer = client.textRenderer;
        Text line = loadedText(loaded);
        int x = (context.getScaledWindowWidth() - renderer.getWidth(line)) / 2;
        int y = context.getScaledWindowHeight() - PotionScopeRules.HUD_BOTTOM_OFFSET;
        context.drawTextWithShadow(renderer, line, x, y, baseColor(loaded));
    }

    /** "Loaded: <shell>" with the shell name in its colour, or "Not loaded". / “已装填：<弹种>”（弹种名着色）或“未装填”。 */
    static Text loadedText(Optional<PotionShellType> loaded) {
        if (loaded.isEmpty()) {
            return Text.translatable(PotionScopeRules.HUD_EMPTY_KEY);
        }
        PotionShellType type = loaded.get();
        Text name = Text.translatable(PotionScopeRules.shellNameKey(type))
                .styled(style -> style.withColor(PotionScopeRules.textColor(type) & 0xFFFFFF));
        return Text.translatable(PotionScopeRules.HUD_LOADED_KEY, name);
    }

    static int baseColor(Optional<PotionShellType> loaded) {
        return loaded.isPresent() ? 0xFFFFFFFF : PotionScopeRules.EMPTY_COLOR;
    }
}
