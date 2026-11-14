package dev.caecorthus.sparkwitch.client.ferryman;

import dev.caecorthus.sparkwitch.roles.neutral.ferryman.FerrymanInstinctVeilComponent;
import dev.caecorthus.sparkwitch.roles.neutral.ferryman.FerrymanRules;
import dev.caecorthus.sparkwitch.roles.neutral.ferryman.FerrymanPlayerComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.CanTargetBody;
import dev.doctor4t.wathe.api.event.GetInstinctHighlight;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The Charon's own view: an attacker holding 瞥视 stays visible through walls in dark red while that attacker is playing
 * and alive in the world, a ferried body carries its 怜悯 mark in pale blue, and a body that is still fresh (died within
 * {@link FerrymanRules#FRESH_BODY_TICKS}, read from Wathe's synced death time) and not ferried yet stays amber so the
 * Charon can find what can still be ferried. The event is client-side and the marks are synced to the owner alone, so
 * nobody else sees these colours; {@code always} makes them independent of the instinct key. The colours never encode
 * whether a body is fake: that test stays server-only, so a feign corpse looks like any other fresh body until the
 * server refuses the ferry.
 * 卡戎自己的视野：带有「瞥视」的攻击者在局内存活、身处世界中时以深红色穿墙可见，已摆渡的遗体以淡蓝色显示「怜悯」标记，
 * 仍然新鲜（死亡不超过 {@link FerrymanRules#FRESH_BODY_TICKS}，读取 Wathe 同步的死亡时间）且尚未摆渡的遗体以琥珀色显示，
 * 便于找到仍可摆渡的遗体。该事件仅客户端且标记只同步给拥有者，因此其他人看不到这些颜色；{@code always} 让它们不依赖
 * 本能键。颜色从不表示遗体是否为假：该判定只在服务端，因此假死尸体看起来与其他新鲜遗体无异，直到服务端拒绝摆渡。
 */
public final class FerrymanClientHighlights {
    /** 瞥视：dark red, as the owner's spec writes it. / 瞥视：深红，按所有者设定。 */
    private static final int GAZE_COLOR = 0x8C1B1B;
    /** 怜悯：pale blue. / 怜悯：淡蓝。 */
    private static final int MERCY_COLOR = 0x8FD3FF;
    /** Fresh, not yet ferried. / 尚未摆渡的新鲜遗体。 */
    private static final int FRESH_BODY_COLOR = 0xFFD27A;
    /** A Lost Soul's 6 block red sight. / 迷失灵魂 6 格内的红色透视。 */
    /** A Lost Soul's sight: teammates cyan, the Charon grey, everyone else red. / 迷失灵魂的视野：队友青、卡戎灰、其余红色。 */
    private static final int CHARON_GREY = 0x9AA0A6;
    private static final int SOUL_ENEMY_COLOR = 0xFF4A4A;
    private static final int PRIORITY = GetInstinctHighlight.HighlightResult.PRIORITY_DEFAULT + 40;
    /**
     * The veil's hide priority: Wathe keeps the highest-priority result, and {@code skip()} carries only
     * {@code PRIORITY_HIGH} (100), which the Fisher glimmer, the Black Raven and SparkTraits Final Moment outlines
     * (101) beat and NoellesRoles Undercover (100) ties, where the earlier listener wins. 300 matches the Fisher /
     * SparkStrength hide.
     * 帷幕的隐藏优先级：Wathe 保留优先级最高的结果，而 {@code skip()} 只带 {@code PRIORITY_HIGH}（100），钓鱼佬灵光、黑羽鸦与
     * SparkTraits 最终时刻的描边（101）都会压过它，NoellesRoles 卧底（100）与之持平，此时先注册者胜出。300 与钓鱼佬 /
     * SparkStrength 的隐藏一致。
     */
    public static final int VEIL_HIDE_PRIORITY = 300;

    private FerrymanClientHighlights() {
    }

    static void register() {
        GetInstinctHighlight.EVENT.register(FerrymanClientHighlights::highlight);
        GetInstinctHighlight.EVENT.register(FerrymanClientHighlights::suppressVeiled);
    }

    /**
     * The 5 s after a feign death: no instinct outline for anyone, the Ferryman included. A hide result (colour -1) at
     * {@link #VEIL_HIDE_PRIORITY} wins over every colouring listener whatever the registration order.
     * 假死结束后的 5 秒：任何人都描不出他，摆渡人也不例外。以 {@link #VEIL_HIDE_PRIORITY} 返回的隐藏结果（颜色 -1）无论注册
     * 顺序如何都压过所有上色监听器。
     */
    private static @Nullable GetInstinctHighlight.HighlightResult suppressVeiled(@Nullable Entity target) {
        if (!(target instanceof PlayerEntity player)) {
            return null;
        }
        return FerrymanInstinctVeilComponent.KEY.get(player).isVeiled()
                ? new GetInstinctHighlight.HighlightResult(-1, false, VEIL_HIDE_PRIORITY)
                : null;
    }

    private static @Nullable GetInstinctHighlight.HighlightResult highlight(@Nullable Entity target) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity self = client.player;
        if (self == null || target == null || target == self) {
            return null;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(self.getWorld());
        Role selfRole = game.getRole(self);
        // A Lost Soul reads the whole lobby through walls: its teammates in cyan, the Charon in grey, everyone else in
        // red. Distance never matters and every mark is local to this client, so nobody else renders these colours.
        // 迷失的灵魂能穿墙看清全场：队友青色、卡戎灰色、其余所有人红色。距离不影响判定，且所有描边都只对本客户端生效，
        // 其他人看到的画面不受影响。
        if (selfRole != null && FerrymanRules.LOST_SOUL_ID.equals(selfRole.identifier())) {
            if (!(target instanceof PlayerEntity other)) {
                return null;
            }
            Role otherRole = game.getRole(other);
            if (otherRole == null) {
                return null;
            }
            if (FerrymanRules.ROLE_ID.equals(otherRole.identifier())) {
                return GetInstinctHighlight.HighlightResult.always(CHARON_GREY, PRIORITY + 10);
            }
            if (FerrymanRules.LOST_SOUL_ID.equals(otherRole.identifier())) {
                return GetInstinctHighlight.HighlightResult.always(FerrymanRules.SOUL_TEAM_COLOR, PRIORITY + 5);
            }
            return GetInstinctHighlight.HighlightResult.always(SOUL_ENEMY_COLOR, PRIORITY);
        }
        // Exact role on a confirmed server, in an ACTIVE round, playing and alive: a dead or swallowed Charon gets no
        // outlines. / 已确认服务器上、ACTIVE 回合中、在局存活的精确职业：死亡或被吞下的卡戎没有描边。
        if (!FerrymanClient.isActiveLocalCharon(self)) {
            return null;
        }
        FerrymanPlayerComponent component = FerrymanPlayerComponent.KEY.get(self);
        if (target instanceof PlayerEntity player) {
            // The Charon always sees his Lost Souls through walls, in their own cyan: during the moment they are his
            // squad, so he must find them the same way they find each other.
            // 卡戎永远能穿墙看见自己的迷失灵魂，用的是灵魂的青色：时刻里他们就是他的队伍，找人方式必须和灵魂之间一致。
            if (isLostSoul(player)) {
                return GetInstinctHighlight.HighlightResult.always(FerrymanRules.SOUL_TEAM_COLOR, PRIORITY + 5);
            }
            return component.hasGaze(player.getUuid()) && isGazeVisible(player)
                    ? GetInstinctHighlight.HighlightResult.always(GAZE_COLOR, PRIORITY)
                    : null;
        }
        if (target instanceof PlayerBodyEntity body) {
            UUID owner = body.getPlayerUuid();
            if (owner == null) {
                return null;
            }
            if (component.hasFerried(owner)) {
                return GetInstinctHighlight.HighlightResult.always(MERCY_COLOR, PRIORITY);
            }
            return isFerriableHint(self, body)
                    ? GetInstinctHighlight.HighlightResult.always(FRESH_BODY_COLOR, PRIORITY)
                    : null;
        }
        return null;
    }

    /** Exact Lost Soul role, read from the same world component as every other mark. / 精确的迷失灵魂职业，与其他描边读同一个世界组件。 */
    private static boolean isLostSoul(PlayerEntity player) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return role != null && FerrymanRules.LOST_SOUL_ID.equals(role.identifier());
    }

    /**
     * A 瞥视 outline only for a marked attacker that is playing and alive and standing in the world, the liveness half
     * of the Black Raven's public-visibility rule ({@code BlackRavenInstinctClientHooks.isPubliclyVisible}): a dead
     * attacker, a spectator (feigning, swallowed, inside a Rift Gate) and a creative player draw nothing. Invisibility
     * is still not read, exactly as before this gate.
     * 「瞥视」描边只给在局存活且身处世界中的被标记攻击者，即黑羽鸦公开可见规则（{@code BlackRavenInstinctClientHooks.
     * isPubliclyVisible}）中的存活部分：已死亡的攻击者、旁观者（假死中、被吞下、在传送门内）与创造模式玩家都不描边。
     * 隐身仍不读取，与加入本门槛之前完全一致。
     */
    static boolean isGazeVisible(PlayerEntity target) {
        return GameFunctions.isPlayerPlayingAndAlive(target) && !GameFunctions.isPlayerSpectatingOrCreative(target);
    }

    /**
     * Amber only for what the client can cheaply tell the server would still consider: a fresh body (Wathe's synced
     * death game time against the client world clock, {@link FerrymanRules#isFreshBody}) that Wathe's
     * {@link CanTargetBody} seam lets this player target. Owner liveness and the fake-body registries are deliberately
     * not read here: an outline that skipped bodies whose owner is alive would point out every Fiend, Charon-feign,
     * Magician or SparkTraits fake corpse across the map.
     * 只对客户端能低成本判断服务端仍会受理的遗体显示琥珀色：新鲜（Wathe 同步的死亡游戏时间对照客户端世界时钟，
     * {@link FerrymanRules#isFreshBody}）且 Wathe 的 {@link CanTargetBody} 接缝允许该玩家选中。此处刻意不读取主人是否存活
     * 与假尸体登记：若描边跳过主人仍存活的遗体，就会隔着整张地图指出每一具魔人、卡戎假死、魔术师或 SparkTraits 的假尸体。
     */
    private static boolean isFerriableHint(ClientPlayerEntity self, PlayerBodyEntity body) {
        return FerrymanRules.isFreshBody(self.getWorld().getTime(), body.getDeathGameTime())
                && CanTargetBody.EVENT.invoker().canTarget(self, body);
    }
}
