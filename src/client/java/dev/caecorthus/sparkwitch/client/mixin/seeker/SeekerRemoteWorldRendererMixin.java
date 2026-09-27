package dev.caecorthus.sparkwitch.client.mixin.seeker;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.caecorthus.sparkwitch.client.seeker.remote.SeekerRemoteViewClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Client-only: renders the owner's own body when it is seen from the car or camera. Vanilla skips every
 * {@code ClientPlayerEntity} that is not the camera focus through the single {@code INSTANCEOF ClientPlayerEntity} in
 * {@code WorldRenderer#render}; in a client world only the local player is a {@code ClientPlayerEntity} (others are
 * {@code OtherClientPlayerEntity}), so reading that check as false while viewing is enough. No {@code @Local} and no
 * new locals: it coexists with Wathe's {@code @Local Entity} and NoellesRoles' {@code CAPTURE_FAILHARD} in the same
 * method. The focused car itself is still skipped by vanilla in first person.
 * <p>Remap: the instanceof is matched with a MixinExtras expression whose {@code @Definition} is a class literal, so
 * the production (intermediary) jar is remapped correctly. {@code @At("CONSTANT", args = "classValue=...")} is NOT
 * remapped by Loom and would miss in production. MixinExtras 0.5+ is guaranteed by the fabricloader floor (0.17.2).
 * 纯客户端：从小车或摄像头看时渲染拥有者自己的本体。原版通过 {@code WorldRenderer#render} 中唯一一处
 * {@code INSTANCEOF ClientPlayerEntity} 跳过所有不是相机焦点的 {@code ClientPlayerEntity}；客户端世界里只有本地
 * 玩家是 {@code ClientPlayerEntity}（其他玩家为 {@code OtherClientPlayerEntity}），因此观看期间把该判断视为 false
 * 即可。不使用 {@code @Local}、不新增局部变量：可与同一方法中 Wathe 的 {@code @Local Entity} 及 NoellesRoles 的
 * {@code CAPTURE_FAILHARD} 共存。第一人称下原版仍会跳过被聚焦的小车本身。
 * 重映射：用 MixinExtras 表达式匹配该 instanceof，其 {@code @Definition} 使用类字面量，因此生产环境（intermediary）
 * jar 能被正确重映射。{@code @At("CONSTANT", args = "classValue=...")} 不会被 Loom 重映射，生产环境会匹配失败。
 * fabricloader 下限（0.17.2）保证 MixinExtras 0.5+ 可用。
 */
@Mixin(WorldRenderer.class)
public abstract class SeekerRemoteWorldRendererMixin {
    @Definition(id = "ClientPlayerEntity", type = ClientPlayerEntity.class)
    @Expression("? instanceof ClientPlayerEntity")
    @ModifyExpressionValue(method = "render", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean sparkwitch$renderOwnBodyWhileViewing(boolean isLocalPlayer) {
        if (!SeekerRemoteViewClient.isActive()) {
            return isLocalPlayer;
        }
        return false;
    }
}
