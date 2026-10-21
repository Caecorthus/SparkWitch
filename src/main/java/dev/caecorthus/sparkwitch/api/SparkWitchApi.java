package dev.caecorthus.sparkwitch.api;

import dev.caecorthus.sparkwitch.component.WraithPlayerComponent;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertStun;
import dev.caecorthus.sparkwitch.roles.civilian.judge.JudgeKillAttribution;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceHits;
import dev.caecorthus.sparkwitch.roles.civilian.seeker.hit.SeekerDeviceRaycast;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecAmmoType;
import dev.caecorthus.sparkwitch.roles.civilian.usec.UsecShieldPierce;
import dev.caecorthus.sparkwitch.roles.civilian.vendetta.VendettaInteractionService;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.BlackRavenCrowEntity;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.crow.hit.BlackRavenCrowHits;
import dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPuppetHits;
import dev.caecorthus.sparkwitch.roles.special.wraith.WraithState;
import dev.doctor4t.wathe.index.tag.WatheItemTags;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Public facade for narrowly scoped downstream compatibility. / 面向下游精确兼容用途的公共门面。 */
public final class SparkWitchApi {
    private static LastEscapeVisionRenderer lastEscapeVisionRenderer;
    private static BlindFeatureGate blindFeatureGate;

    /** Client-installed renderer; the common facade has no client class references. */
    @FunctionalInterface
    public interface LastEscapeVisionRenderer {
        boolean render(PlayerEntity player, float delta);
    }

    public static void installLastEscapeVisionRenderer(LastEscapeVisionRenderer renderer) {
        lastEscapeVisionRenderer = renderer;
    }

    /** Version 1 consumes Traits' three parameters and owns the sole combined escape pass.
     * 版本 1 接收 Traits 的三个参数并负责唯一合成后处理；服务端及未初始化时为 0。 */
    public static int getLastEscapeVisionProtocolVersion() {
        return lastEscapeVisionRenderer == null ? 0 : 1;
    }

    public static boolean supportsLastEscapeVision() {
        return getLastEscapeVisionProtocolVersion() == 1;
    }

    /**
     * Render-thread only. True means a composed pass was actually rendered, not merely supported.
     * False (including dedicated servers and shader load failure) lets Traits render its fallback.
     * 仅在渲染线程调用；成功执行合成后处理才返回 true，失败时由 Traits 回退渲染。
     */
    public static boolean tryRenderLastEscapeVision(PlayerEntity player, float delta) {
        return player != null && lastEscapeVisionRenderer != null && lastEscapeVisionRenderer.render(player, delta);
    }

    /**
     * Client-installed Blind feature gate; the common facade has no client class references.
     * 客户端安装的盲人附加层闸门；公共门面不引用任何客户端类。
     */
    @FunctionalInterface
    public interface BlindFeatureGate {
        boolean hidesFeatures(PlayerEntity player);
    }

    public static void installBlindFeatureGate(BlindFeatureGate gate) {
        blindFeatureGate = gate;
    }

    /**
     * Render thread, client presentation only. True while the local Blind's view strips every feature (held items,
     * armor, capes, mod extras) from {@code player}'s body, in the world frame and in the Blind's silhouette pass.
     * SparkWitch skips those itself inside {@code LivingEntityRenderer}'s feature loop. A downstream renderer that
     * draws a feature-like extra outside that loop skips it while this holds; today that is SparkStrength's skateboard
     * under a body another mod replaced (the SparkTraits Pig). The body itself is never gated by this answer: owner
     * 2026-10-07, a perceived Pig keeps its pig outline. False on a server, before the client installs the gate, and
     * for null.
     * 仅限渲染线程、仅用于客户端展示。本地盲人的视图在世界画面与盲人轮廓 pass 中去掉 {@code player} 身体上的全部附加层
     * （手持物、护甲、披风、模组附加物）时为真。SparkWitch 自己在 {@code LivingEntityRenderer} 的附加层循环内跳过它们；
     * 在该循环之外绘制类附加层物件的下游渲染器，在此为真时应跳过该物件，目前即 SparkStrength 在被其他模组替换的身体
     * （SparkTraits 猪）下方绘制的滑板。身体本身从不受此答案控制：所有者 2026-10-07 决定，被感知的猪保留猪形轮廓。
     * 服务端、客户端尚未安装闸门以及参数为 null 时返回 false。
     */
    public static boolean hidesFeaturesFromBlind(PlayerEntity player) {
        return player != null && blindFeatureGate != null && blindFeatureGate.hidesFeatures(player);
    }

    private SparkWitchApi() {
    }

    /** Preserves the responsible UUID for one synchronous lethal action, including offline owners.
     * 为一次同步致死操作保留责任 UUID，包含离线责任人；嵌套调用和异常会恢复原上下文。 */
    public static void runWithKillAttribution(ServerWorld world, UUID responsiblePlayer, Runnable action) {
        JudgeKillAttribution.runWith(world, responsiblePlayer, action);
    }

    /**
     * True while a Control Expert stun locks {@code player}'s input (the server copy is authoritative). For add-on
     * sessions the payload deny-list cannot end, such as SparkStrength's drone pilot.
     * 控场专家眩晕锁定该玩家输入时为 true（以服务端为准）。供附属模组结束数据包拦截名单无法结束的会话，例如 SparkStrength
     * 的无人机驾驶。
     */
    public static boolean isControlExpertStunned(PlayerEntity player) {
        return ControlExpertStun.isStunned(player);
    }

    /**
     * Frozen cross-mod seam (2026-10-07), client side: aim for an add-on gun that picks its target with Wathe's
     * {@code RevolverItem.getGunTarget} but fires through its own payload instead of {@code RevolverItem#use} (today
     * SparkStrength's Serial Killer pistols). {@code getGunTarget} itself already carries SparkWitch's active-Wraith
     * pass-through and nearer-Magician-puppet pick; this adds the one step SparkWitch wraps inside
     * {@code RevolverItem#use}: a Seeker device strictly nearer on the look ray (anywhere on it when nothing was hit)
     * replaces {@code gunTarget}, the shooter's own devices excepted. Feed the result to
     * {@code RevolverItem.resolveTargetFromHitResult}, so the gun sends the id a revolver would. {@code range} is only
     * the ray length when {@code gunTarget} has no usable position. Returns {@code gunTarget} unchanged when nothing
     * applies or an argument is null. Side-neutral, but meant for the client pick.
     * 冻结的跨模组接缝（2026-10-07），客户端：供以 Wathe {@code RevolverItem.getGunTarget} 选靶、却经自有数据包而非
     * {@code RevolverItem#use} 开火的附属模组枪械使用（目前为 SparkStrength 连环杀手手枪）。{@code getGunTarget} 本身已带有
     * SparkWitch 的激活冤魂穿透与更近魔术师皮套选取；这里补上 SparkWitch 在 {@code RevolverItem#use} 内包装的那一步：视线上
     * 严格更近（未命中时为射线上任意处）的搜寻者设备替换 {@code gunTarget}，射手自己的设备除外。将结果交给
     * {@code RevolverItem.resolveTargetFromHitResult}，枪械便发送与左轮相同的 id。{@code range} 仅在 {@code gunTarget}
     * 没有可用位置时作为射线长度。无适用情况或参数为 null 时原样返回 {@code gunTarget}。两端通用，但用于客户端选靶。
     */
    public static HitResult preferNearerGunWorldTarget(PlayerEntity shooter, HitResult gunTarget, double range) {
        return SeekerDeviceRaycast.preferNearerDevice(shooter, gunTarget, range);
    }

    /**
     * Frozen cross-mod seam (2026-10-07), server thread only: the SparkWitch world-entity part of an add-on gun shot
     * whose client sent a non-player target id (today SparkStrength's Serial Killer pistols). Call it where Wathe's
     * revolver receiver records the shot: after the shot was accepted and its click sound played, before the record,
     * the kill and the cooldown. A live Magician puppet that is not the shooter's own ends as under a revolver hit
     * (D3–D5: decoy, 50 coins to the Magician); a Seeker device the shooter may break breaks as under a revolver hit.
     * Both re-check aim and line of sight within {@code maxDistance} (capped at Wathe's 65). {@code gun} must be the
     * {@code wathe:guns} stack in the shooter's main or off hand, not cooling down. True when the shot ended a puppet
     * or broke a device. A Black Raven Raven Form crow the shooter hits (same aim, line and distance checks) kills
     * the Black Raven as this gun kills a player, inside this call: the SparkFactionAPI gun veto, the
     * {@code ShouldPunishGunShooter} custom punishment, then the kill ({@code wathe:gun}); true as well. Either way a
     * non-player target leaves the shot a miss for the caller: record it with no target, play the shot, set the
     * cooldown, never call {@code killPlayer} or apply a gun punishment itself. False for players, nulls and anything
     * else.
     * 冻结的跨模组接缝（2026-10-07），仅服务端线程：附属模组枪械射击中、客户端发送了非玩家目标 id 时属于 SparkWitch
     * 世界实体的部分（目前为 SparkStrength 连环杀手手枪）。在 Wathe 左轮接收器记录这一枪的位置调用：射击已被接受、扳机声已
     * 播放之后，记录、击杀与冷却之前。不属于射手自己的活魔术师皮套按左轮命中结束（D3–D5：诱饵、向魔术师支付 50 金币）；射手
     * 可打坏的搜寻者设备按左轮命中打坏。两者都会在 {@code maxDistance}（上限为 Wathe 的 65）内复查瞄准与视线。{@code gun}
     * 须为射手主手或副手中、未冷却的 {@code wathe:guns} 物品。结束皮套或打坏设备时返回 true。射手命中黑羽鸦的化鸦乌鸦（同样的
     * 瞄准、视线与距离检查）时，在本调用内按该枪击杀玩家的方式处死黑羽鸦：SparkFactionAPI 枪械否决、
     * {@code ShouldPunishGunShooter} 自定义惩罚，然后击杀（{@code wathe:gun}），同样返回 true。无论结果如何，非玩家目标对调用方
     * 都是一次未命中：以无目标记录、播放枪声、设置冷却，自身绝不调用 {@code killPlayer} 或施加枪械惩罚。玩家、null 及其他情况
     * 返回 false。
     */
    public static boolean hitGunWorldTarget(ServerPlayerEntity shooter, Entity target, ItemStack gun,
                                            double maxDistance) {
        if (shooter == null || target == null || gun == null || target instanceof PlayerEntity
                || shooter.getWorld().isClient() || shooter.isSpectator() || !gun.isIn(WatheItemTags.GUNS)
                || (shooter.getMainHandStack() != gun && shooter.getOffHandStack() != gun)
                || shooter.getItemCooldownManager().isCoolingDown(gun.getItem())) {
            return false;
        }
        return MagicianPuppetHits.onAddonGunShot(shooter, target, gun, maxDistance)
                || SeekerDeviceHits.onAddonGunShot(shooter, target, maxDistance)
                || BlackRavenCrowHits.onAddonGunShot(shooter, target, maxDistance);
    }

    /**
     * Frozen cross-mod seam (2026-10-21), side-neutral: true when {@code entity} is a Black Raven Raven Form crow (化鸦)
     * that is not removed. For an add-on weapon whose client gate accepts only player targets (today SparkStrength's
     * Undercover tomahawk), so it still sends the attack on a crow; the server then decides through
     * {@link #crowMeleeStandIn}. A crow carries no name tag and no role, so this reveals nothing the model does not.
     * False for null and every other entity.
     * 冻结的跨模组接缝（2026-10-21），两端通用：{@code entity} 是未被移除的黑羽鸦化鸦乌鸦时为 true。供客户端门槛只接受玩家目标的
     * 附属模组武器使用（目前为 SparkStrength 卧底战术斧），使其对乌鸦也发送攻击；随后由服务端经 {@link #crowMeleeStandIn} 判定。
     * 乌鸦没有名牌也没有职业，因此本方法不泄露模型以外的任何信息。参数为 null 及其他任何实体时返回 false。
     */
    public static boolean isBlackRavenCrow(@Nullable Entity entity) {
        return entity instanceof BlackRavenCrowEntity crow && !crow.isRemoved();
    }

    /**
     * Frozen cross-mod seam (2026-10-21), server thread only: owner substitution for an add-on melee weapon that kills a
     * player target through its own path (today SparkStrength's Veteran, Coroner-disguise and Silencer knives and the
     * Undercover tomahawk). When {@code target} is a live Raven Form crow that {@code attacker} may hit (alive in
     * survival or adventure, never its owner), within the weapon's own {@code reach} plus a 0.5 latency tolerance from
     * the attacker's eye to the crow's lag-compensated hitbox, with a clear line, this returns the crow's owner (the
     * Black Raven) and arms the kill link for this server tick. The caller then runs its own player path on that owner,
     * skipping only its own distance check on the body (the crow was measured here): its gates, faction veto, parry,
     * record, {@code GameFunctions.killPlayer} with its own death reason, and its own innocent-kill rule, so the wielder
     * pays exactly what killing the Raven directly costs. That {@code killPlayer} first ends the Raven Form session
     * (crow killed, no Homing Feather); a path that never kills leaves the link to expire at the end of the tick. Null,
     * arming nothing, for players, nulls, any other entity, a crow out of reach or behind a wall, and off the server.
     * 冻结的跨模组接缝（2026-10-21），仅服务端线程：供经自身路径击杀玩家目标的附属模组近战武器使用的主人替身（目前为
     * SparkStrength 老兵、验尸官伪装与沉默者的刀以及卧底战术斧）。{@code target} 是 {@code attacker} 可命中的存活化鸦乌鸦
     * （攻击者存活且为生存或冒险模式，且绝不是其主人），从攻击者眼睛到乌鸦延迟补偿命中箱的距离在武器自身 {@code reach} 加 0.5
     * 延迟容差之内，且视线畅通时，返回乌鸦的主人（黑羽鸦）并为本服务端刻装好击杀链接。调用方随后对该主人执行自身的玩家路径，
     * 只跳过自身对本体的距离检查（乌鸦已在此量过）：其门槛、阵营否决、格挡、记录、带自身死因的 {@code GameFunctions.killPlayer}
     * 以及自身的误杀规则，因此持械者付出的代价与直接杀死黑羽鸦完全相同。该 {@code killPlayer} 会先结束化鸦会话（乌鸦被杀，不使用
     * 归羽）；从未击杀的路径让链接在本刻结束时失效。玩家、null、其他任何实体、超出距离或墙后的乌鸦以及不在服务端时返回 null，
     * 且不装入任何替身。
     */
    public static @Nullable ServerPlayerEntity crowMeleeStandIn(ServerPlayerEntity attacker, @Nullable Entity target,
                                                                double reach) {
        return BlackRavenCrowHits.addonMeleeStandIn(attacker, target, reach);
    }

    /**
     * Frozen cross-mod seam (2026-10-21), server thread only: for an add-on blast that kills the players in it on its
     * own (today SparkStrength's Bomber bomb drone), called once after its player kills. Every live Raven Form crow
     * whose box centre lies within {@code radius} of {@code center} with a clear line from the centre (the measure the
     * SparkStrength M67 gets) kills its owner: the session ends (crow killed, no Homing Feather), then
     * {@code GameFunctions.killPlayer(owner, true, killer, deathReason)} with {@code deathReason} passed through as the
     * same instance, so reference-compared rewards and every kill protection see it as the blast's own kill. Never the
     * killer's own crow; only while a Wathe round runs. Returns how many crows were hit; 0 for nulls, a negative or
     * non-finite radius, and off a running round.
     * 冻结的跨模组接缝（2026-10-21），仅服务端线程：供自行击杀范围内玩家的附属模组爆炸使用（目前为 SparkStrength 炸弹客炸弹
     * 无人机），在其玩家击杀之后调用一次。箱体中心位于 {@code center} 的 {@code radius} 范围内且与爆心视线畅通（与 SparkStrength
     * M67 相同的量法）的每只存活化鸦乌鸦处死其主人：先结束会话（乌鸦被杀，不使用归羽），再以按同一实例原样传递的
     * {@code deathReason} 调用 {@code GameFunctions.killPlayer(owner, true, killer, deathReason)}，因此按引用比较的奖励与所有
     * 击杀保护都把它视为该爆炸自身的击杀。从不包括击杀者自己的乌鸦；仅在 Wathe 对局进行中生效。返回被命中的乌鸦数量；参数为
     * null、半径为负或非有限、以及对局未进行时返回 0。
     */
    public static int killCrowsInBlast(ServerWorld world, Vec3d center, double radius,
                                       @Nullable ServerPlayerEntity killer, Identifier deathReason) {
        return BlackRavenCrowHits.onBlast(world, center, radius, killer, deathReason);
    }

    /**
     * Frozen cross-mod seam (2026-10-07), server authority: true when {@code target} is the active Vendetta bound to
     * {@code actor}. Wathe counts an active Vendetta as dead, yet its bound killer may still aim at it and finish it:
     * {@code GameFunctions.killPlayer} by that killer resolves the Vendetta's terminal death, as a Wathe revolver shot
     * does. For an add-on weapon whose own target check rejects Wathe-dead players (today SparkStrength's Serial Killer
     * pistols); SparkWitch's NoellesRoles target mixins use the same rule. False for nulls and the same player.
     * 冻结的跨模组接缝（2026-10-07），以服务端为准：{@code target} 是与 {@code actor} 绑定的激活仇杀客时为 true。Wathe 将激活的
     * 仇杀客视为已死亡，但其绑定凶手仍可瞄准并了结它：由该凶手调用 {@code GameFunctions.killPlayer} 会结算仇杀客的终局死亡，
     * 与 Wathe 左轮射击一致。供自身目标检查会拒绝 Wathe 已死亡玩家的附属模组武器使用（目前为 SparkStrength 连环杀手手枪）；
     * SparkWitch 对 NoellesRoles 的目标 mixin 使用同一规则。参数为 null 或同一玩家时返回 false。
     */
    public static boolean isBoundKillerTargetingVendetta(PlayerEntity actor, PlayerEntity target) {
        return VendettaInteractionService.isBoundKillerTargetingVendetta(actor, target);
    }

    /**
     * Frozen cross-mod seam (2026-10-07), server thread only: an add-on shield layer that is about to stop a kill asks
     * this first. True spends one pierce of the shield-piercing shot being settled against exactly this
     * {@code victim}, {@code killer} and {@code deathReason}; the caller then spends its layer as if it had blocked
     * and lets the same kill go on. Today the only such shot is the USEC AXMC's {@code wathe:gun_shot} (FMJ 2 layers,
     * AP 5, +1 with SparkTraits Heavy Artillery), and the only add-on layer is SparkStrength's Bodyguard vest (owner
     * 2026-10-07). False, spending nothing, for every other kill, with the budget empty, off the server thread and for
     * nulls; the caller then blocks as usual.
     * 冻结的跨模组接缝（2026-10-07），仅服务端线程：即将挡下一次击杀的附属模组护盾层先询问这里。返回 true 即花费正对确切的
     * {@code victim}、{@code killer} 与 {@code deathReason} 结算的穿盾射击的一次穿透；调用方随后如同挡下一样消耗该层，并放行同一
     * 次击杀。目前唯一的此类射击是 USEC AXMC 的 {@code wathe:gun_shot}（FMJ 2 层，AP 5 层，SparkTraits 重炮手再 +1），唯一的
     * 附属模组护盾层是 SparkStrength 保镖防弹衣（所有者 2026-10-07）。其他任何击杀、预算已空、不在服务端线程以及参数为 null 时
     * 返回 false 且不花费任何预算，调用方照常挡下。
     */
    public static boolean tryPierceShieldLayer(PlayerEntity victim, PlayerEntity killer, Identifier deathReason) {
        return UsecShieldPierce.tryPierce(victim, killer, deathReason);
    }

    /**
     * Frozen cross-mod seam (2026-10-07), server thread only: the stable round id ({@code "fmj"} or {@code "ap"},
     * {@code UsecAmmoType.id()}) of the shield-piercing shot being settled against exactly this {@code victim},
     * {@code killer} and {@code deathReason}, pierce budget left or not; null for every other kill (revolvers
     * included), off the server thread and for nulls. Spends nothing. For an add-on shield the shot never pierces that
     * prices its block by round; the add-on owns the price (owner 2026-10-07: SparkStrength's Democracy Shield, 10
     * stamina points for FMJ, 25 for AP). Treat an unknown id as "not a piercing shot".
     * 冻结的跨模组接缝（2026-10-07），仅服务端线程：正对确切的 {@code victim}、{@code killer} 与 {@code deathReason} 结算的穿盾
     * 射击的稳定弹种 id（{@code "fmj"} 或 {@code "ap"}，即 {@code UsecAmmoType.id()}），不论穿透预算是否剩余；其他任何击杀（含
     * 左轮）、不在服务端线程以及参数为 null 时为 null。不花费任何预算。供该射击无法击穿、按弹种为格挡定价的附属模组盾牌使用；
     * 价格归附属模组所有（所有者 2026-10-07：SparkStrength 民主盾牌，FMJ 10 点体力，AP 25 点）。未知 id 应视为“非穿盾射击”。
     */
    public static @Nullable String piercingShotAmmoId(PlayerEntity victim, PlayerEntity killer, Identifier deathReason) {
        UsecAmmoType ammo = UsecShieldPierce.scopedAmmo(victim, killer, deathReason);
        return ammo == null ? null : ammo.id();
    }

    public static boolean isWraithActive(PlayerEntity player) {
        return player != null
                && WraithPlayerComponent.KEY.maybeGet(player)
                .map(WraithPlayerComponent::isActive)
                .orElse(false);
    }

    /**
     * Returns whether the player is an active Wraith whose owner-visible saved alignment is KILLER.
     * Redacted client records and invalid persisted state fail closed rather than leaking an alignment guess.
     */
    public static boolean isKillerAlignedWraith(PlayerEntity player) {
        return player != null
                && WraithPlayerComponent.KEY.maybeGet(player)
                .map(wraith -> isKillerAlignedWraith(wraith.isActive(), wraith.getAlignment()))
                .orElse(false);
    }

    static boolean isKillerAlignedWraith(boolean active, WraithState.Alignment alignment) {
        return active && alignment == WraithState.Alignment.KILLER;
    }

    public static boolean isWraithRestricted(PlayerEntity player) {
        return player != null
                && WraithPlayerComponent.KEY.maybeGet(player)
                .map(WraithPlayerComponent::isRestricted)
                .orElse(false);
    }

    public static boolean isWraithPromoted(PlayerEntity player) {
        return player != null
                && WraithPlayerComponent.KEY.maybeGet(player)
                .map(WraithPlayerComponent::isPromoted)
                .orElse(false);
    }
}
