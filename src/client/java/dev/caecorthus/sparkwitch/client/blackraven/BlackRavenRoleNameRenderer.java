package dev.caecorthus.sparkwitch.client.blackraven;

import dev.caecorthus.sparkwitch.client.magician.MagicianPuppetNameTags;
import dev.caecorthus.sparkwitch.client.render.WraithNameTagPassThrough;
import dev.caecorthus.sparkwitch.client.text.WitchRoleDisplayTexts;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenIdentitySnapshot;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenPerceptionPlayerComponent;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/** Owns Black Raven's sensed nearby-identity policy and rendering. / 负责黑羽鸦已感知近身身份的判定与渲染。 */
public final class BlackRavenRoleNameRenderer {
    private BlackRavenRoleNameRenderer() {
    }

    public static void render(
            TextRenderer renderer,
            ClientPlayerEntity player,
            DrawContext context,
            @Nullable Text nametag,
            float nametagAlpha
    ) {
        if (!BlackRavenClientState.isEligible(player)
                || !BlackRavenClientState.isSensedMode()
                || BlackRavenClientState.isPerceptionActive(player)
                || nametagAlpha <= 0.05f
                || nametag == null
                || nametag.getString().isBlank()) {
            return;
        }

        BlockPos eyeBlock = BlockPos.ofFloored(player.getEyePos());
        if (player.getWorld().getLightLevel(LightType.BLOCK, eyeBlock) < 3
                && player.getWorld().getLightLevel(LightType.SKY, eyeBlock) < 10) {
            return;
        }
        // Same Wraith pass-through and Magician puppet stand-in as Wathe's name tag, so the sensed role follows the
        // tagged player. 与 Wathe 名牌相同的冤魂穿透与魔术师皮套替身，使感知身份跟随被显示名牌的玩家。
        Predicate<Entity> nameTarget = WraithNameTagPassThrough.filterNameTarget(
                player,
                entity -> entity instanceof PlayerEntity
        );
        if (!(MagicianPuppetNameTags.asCopiedPlayer(ProjectileUtil.getCollision(
                player, MagicianPuppetNameTags.stopAtPuppets(nameTarget), 2.0))
                instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof PlayerEntity target)
                || !BlackRavenInstinctClientHooks.isPubliclyVisible(player, target)) {
            return;
        }

        GetInstinctHighlight.HighlightResult result = GetInstinctHighlight.EVENT.invoker().getHighlight(target);
        if (result != null && result.isSkip()) {
            return;
        }

        BlackRavenPerceptionPlayerComponent component = BlackRavenPerceptionPlayerComponent.KEY.get(player);
        BlackRavenIdentitySnapshot snapshot = component.snapshot(target.getUuid());
        if (snapshot == null) {
            return;
        }

        Text roleText = WitchRoleDisplayTexts.roleName(snapshot.roleTranslationKey());
        int alpha = (int) (nametagAlpha * 255.0f) << 24;
        int color = snapshot.roleColor() | alpha;
        context.getMatrices().push();
        context.getMatrices().translate(
                context.getScaledWindowWidth() / 2f,
                context.getScaledWindowHeight() / 2f + 6,
                0
        );
        context.getMatrices().scale(0.6f, 0.6f, 1f);
        context.drawTextWithShadow(renderer, roleText, -renderer.getWidth(roleText) / 2, 0, color);
        context.getMatrices().pop();
    }
}
