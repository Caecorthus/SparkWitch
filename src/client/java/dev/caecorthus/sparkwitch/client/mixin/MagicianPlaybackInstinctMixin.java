package dev.caecorthus.sparkwitch.client.mixin;

import dev.caecorthus.sparkwitch.client.magician.MagicianPuppetStandIn;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPlaybackEntity;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A Magician puppet glows exactly as the player it copies would for this viewer (owner decision D6): a puppet that never
 * glows while every player does would be a tell. Wathe's outline ({@code hasOutline}) and its colour (the
 * {@code getTeamColorValue} read in {@code WorldRenderer#render}) both ask {@code getInstinctHighlight}, so a puppet is
 * answered by re-asking the public method for its stand-in player; that call runs every mod's events, HEAD answers,
 * RETURN modifiers and wrappers (Wathe killer instinct, SparkFactionAPI and witch-faction colours, Wraith privacy, Black
 * Raven, Fiend Moment, Obscure/Fear, Perfumer, ...) on that player. HEAD callbacks run in ascending mixin priority, so
 * 600 answers right after the Fiend Moment HEAD (500, which ignores non-players) and before SparkTraits (1000), Wraith
 * and Black Raven (2000) ever see the puppet. Wrappers around the method (Obscure/Fear) also judge the puppet itself
 * first, as a viewer-level veto. The Blind's {@code hasOutline} veto still answers false. Presentation only.
 * 魔术师皮套对本观察者的发光与其复制的玩家完全一致（所有者决定 D6）：所有玩家都发光而皮套从不发光会暴露身份。Wathe 的描边
 * （{@code hasOutline}）与颜色（{@code WorldRenderer#render} 中读取的 {@code getTeamColorValue}）都询问
 * {@code getInstinctHighlight}，因此对皮套改为以其替身玩家重新调用该公开方法，所有模组的事件、HEAD 结果、RETURN 修改与包装
 * （Wathe 杀手本能、SparkFactionAPI 与魔女阵营颜色、冤魂隐私、黑羽鸦、魔人时刻、障眼/恐惧、调香师等）都对该玩家生效。HEAD
 * 回调按 mixin 优先级升序执行，600 紧随魔人时刻 HEAD（500，忽略非玩家）作答，SparkTraits（1000）、冤魂与黑羽鸦（2000）
 * 不会看到皮套本身。方法外层的包装（障眼/恐惧）仍会先对皮套作观察者级否决。盲人的 {@code hasOutline} 否决仍返回 false。
 * 仅为显示。
 */
@Mixin(value = WatheClient.class, remap = false, priority = 600)
public abstract class MagicianPlaybackInstinctMixin {
    @Inject(method = "getInstinctHighlight", at = @At("HEAD"), cancellable = true)
    private static void sparkwitch$highlightPuppetAsCopiedPlayer(Entity target, CallbackInfoReturnable<Integer> cir) {
        if (target instanceof MagicianPlaybackEntity puppet) {
            PlayerEntity standIn = MagicianPuppetStandIn.of(puppet);
            cir.setReturnValue(standIn == null ? -1 : WatheClient.getInstinctHighlight(standIn));
        }
    }
}
