package dev.caecorthus.sparkwitch.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.caecorthus.sparkwitch.client.magician.MagicianPuppetNameTags;
import dev.doctor4t.wathe.client.gui.RoleNameRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Predicate;

/**
 * Labels a Magician puppet through Wathe's own player name tag (owner decision D6). Wathe's first {@code renderHud}
 * raycast accepts only players, so it used to pass through a puppet and label the player behind it. {@code @ModifyArg}
 * widens that predicate so a live puppet stops the ray (it composes with {@code WraithNameTagRaycastMixin}, which only
 * narrows players), and {@code @ModifyExpressionValue} reports a puppet hit as its stand-in player, so Wathe draws the
 * copied player's label with its own darkness, range, fade, psycho, spectator and cohort rules, and every injection on
 * the name ({@code WraithNameMixin} blanking included) sees that player. The body and note raycasts are untouched.
 * 通过 Wathe 自己的玩家名牌显示魔术师皮套（所有者决定 D6）。Wathe {@code renderHud} 的第一条射线只接受玩家，因此过去会穿过
 * 皮套给身后的玩家贴名牌。{@code @ModifyArg} 放宽该判定，使存活皮套挡住射线（与只收窄玩家的
 * {@code WraithNameTagRaycastMixin} 可共存）；{@code @ModifyExpressionValue} 将皮套命中改报为其替身玩家，于是 Wathe 以自身的
 * 黑暗、距离、淡入淡出、疯魔、旁观与同伙规则绘制被复制玩家的名牌，名字上的所有注入（包括 {@code WraithNameMixin} 的隐藏）
 * 看到的也是该玩家。尸体与纸条射线不受影响。
 */
@Mixin(RoleNameRenderer.class)
public abstract class MagicianPlaybackRoleNameMixin {
    @ModifyArg(
            method = "renderHud",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/projectile/ProjectileUtil;getCollision(Lnet/minecraft/entity/Entity;Ljava/util/function/Predicate;D)Lnet/minecraft/util/hit/HitResult;",
                    ordinal = 0
            ),
            index = 1
    )
    private static Predicate<Entity> sparkwitch$nameTagStopsAtPuppets(
            Entity viewer,
            Predicate<Entity> predicate,
            double range
    ) {
        return MagicianPuppetNameTags.stopAtPuppets(predicate);
    }

    @ModifyExpressionValue(
            method = "renderHud",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/projectile/ProjectileUtil;getCollision(Lnet/minecraft/entity/Entity;Ljava/util/function/Predicate;D)Lnet/minecraft/util/hit/HitResult;",
                    ordinal = 0
            )
    )
    private static HitResult sparkwitch$labelPuppetAsCopiedPlayer(HitResult hit) {
        return MagicianPuppetNameTags.asCopiedPlayer(hit);
    }
}
