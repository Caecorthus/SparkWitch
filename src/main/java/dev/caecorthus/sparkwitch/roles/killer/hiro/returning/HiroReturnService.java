package dev.caecorthus.sparkwitch.roles.killer.hiro.returning;

import dev.caecorthus.sparkwitch.roles.killer.hiro.HiroRules;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 二阶堂希罗's 死亡回归 (Return by Death), server-authoritative. SCAFFOLD: only the state lifecycle is wired (the
 * opening cooldown at role assignment, a clear on player reset); the skill itself is in progress.
 * <p>Owner spec (2026-11-11): the ability key (G) costs {@link HiroRules#RETURN_MANA_COST} mana, records the caster's
 * position and opens a {@link HiroRules#RETURN_WINDOW_TICKS} window. Everyone Hiro kills inside it enters a fake death
 * ({@link HiroRules#VICTIM_FEIGN_TICKS} each). If Hiro is attacked (a kill attempt) the attack is cancelled, she gains
 * {@link HiroRules#RETURN_REWARD_COINS} coins, returns to the record point, glows for
 * {@link HiroRules#RETURN_GLOW_TICKS} and gets a {@link HiroRules#RETURN_ITEM_LOCK_TICKS} all-item cooldown, and the
 * fake-dead victims revive; otherwise they die for real (killer Hiro, original death reason). First use
 * {@link HiroRules#OPENING_COOLDOWN_TICKS} after role assignment; {@link HiroRules#RETURN_COOLDOWN_TICKS} cooldown from
 * the moment the skill ends.
 * 二阶堂希罗的「死亡回归」，服务端权威。骨架阶段：只接好了状态生命周期（分配职业时的开局冷却、玩家重置时清除），技能本身仍在
 * 实现中。
 * <p>所有者规格（2026-11-11）：技能键（G）消耗 {@link HiroRules#RETURN_MANA_COST} 魔力，记录施放者位置并开启
 * {@link HiroRules#RETURN_WINDOW_TICKS} 的窗口。窗口内被希罗击杀的玩家进入假死（各 {@link HiroRules#VICTIM_FEIGN_TICKS}）。
 * 若希罗遭到攻击（击杀尝试），该攻击被取消，她获得 {@link HiroRules#RETURN_REWARD_COINS} 金币、回到记录点、发光
 * {@link HiroRules#RETURN_GLOW_TICKS} 并进入 {@link HiroRules#RETURN_ITEM_LOCK_TICKS} 的全物品冷却，假死的受害者复活；否则他们
 * 真正死亡（凶手为希罗，沿用原死因）。分配职业后 {@link HiroRules#OPENING_COOLDOWN_TICKS} 才能首次使用；冷却
 * {@link HiroRules#RETURN_COOLDOWN_TICKS} 从技能结束时开始计算。
 */
public final class HiroReturnService {
    private static boolean registered;

    private HiroReturnService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                HiroReturnComponent.KEY.get(serverPlayer).assignForRole(role);
            }
        });
        ResetPlayer.EVENT.register(player -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                HiroReturnComponent.KEY.get(serverPlayer).clear();
            }
        });
        // TODO(死亡回归): window expiry and settlement, the kill interception for Hiro and her victims, round-end
        // clears, the replay formatter for HiroRules.RETURN_SKILL_ID.
        // TODO（死亡回归）：窗口到期与结算、对希罗及其受害者的击杀拦截、回合结束清理、HiroRules.RETURN_SKILL_ID 的回放格式化器。
    }

    /**
     * Cross-feature seam (stable signature): whether {@code player} is currently one of Hiro's fake-dead victims (alive
     * in Wathe, spectating its own fake corpse, waiting for its own timer). The Rope treats a kill that ended in this
     * state as a successful kill.
     * 跨功能接缝（签名稳定）：{@code player} 此刻是否是希罗的假死受害者（在 Wathe 中仍存活、旁观自己的假尸体、等待各自的计时）。
     * 绳索把以此状态结束的击杀视为成功击杀。
     */
    public static boolean isFeignedVictim(PlayerEntity player) {
        return false;
    }

    /**
     * Server entry of the ability key ({@code sparkwitch:use_hiro_skill}, an empty request). SCAFFOLD: validates only
     * that the sender is a living raw-role Hiro in an ACTIVE round; a refused press changes nothing.
     * 技能键的服务端入口（{@code sparkwitch:use_hiro_skill}，空请求）。骨架阶段：只校验发送者在 ACTIVE 回合中是存活的真实身份
     * 希罗；被拒绝的按键不改变任何东西。
     */
    public static void use(ServerPlayerEntity caster) {
        if (caster == null
                || GameWorldComponent.KEY.get(caster.getWorld()).getGameStatus()
                        != GameWorldComponent.GameStatus.ACTIVE
                || !HiroRules.isLivingHiro(caster)) {
            return;
        }
        // TODO(死亡回归): the full caster gate (SpecterRules.mayCast pattern: swallowed, held, silenced, SparkTraits
        // role-skill block, then GrandWitchFearService.denyRoleSkillIfFeared), the cooldown and running-window checks
        // on HiroReturnComponent, then the mana cost:
        //     WitchPlayerComponent.KEY.get(caster).spendMana(HiroRules.RETURN_MANA_COST)
        // (dev.caecorthus.sparkwitch.component.WitchPlayerComponent#spendMana(int) returns false and spends nothing
        // when the mana system is off or the balance is short), and only then
        //     HiroReturnComponent#openWindow(world.getTime() + HiroRules.RETURN_WINDOW_TICKS, RecordPoint.of(caster)).
        // TODO（死亡回归）：完整的施放者门槛（SpecterRules.mayCast 模式：被吞下、被控制、被沉默、SparkTraits 职业技能封锁，随后
        // GrandWitchFearService.denyRoleSkillIfFeared）、HiroReturnComponent 上的冷却与进行中窗口检查，然后扣除魔力：
        //     WitchPlayerComponent.KEY.get(caster).spendMana(HiroRules.RETURN_MANA_COST)
        // （dev.caecorthus.sparkwitch.component.WitchPlayerComponent#spendMana(int)：魔力系统未启用或余额不足时返回 false 且
        // 不扣除），成功后才调用
        //     HiroReturnComponent#openWindow(world.getTime() + HiroRules.RETURN_WINDOW_TICKS, RecordPoint.of(caster))。
    }
}
