package dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.adapter;

import dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.BlackRavenDisguiseRules;
import java.util.List;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * One claimable role's explicit parity surface. Everything the acting overlay (isRole widening) cannot
 * reach is declared here: the first-entry kit and cooldowns, enter/exit side effects, switch-blocking
 * windows, the shop whitelist, money visibility, and the task-income payer. Server-only hooks receive
 * a ServerPlayerEntity; {@link #shopSpec()}, {@link #moneyVisible()} and {@link #taskReward()} are
 * constant per role and are read on both sides. Adapters never fire RoleAssigned, call addRole,
 * initializeShop or setBalance, or tag stacks with CUSTOM_DATA.
 * 单个可伪装职业的显式对等面。扮演覆盖层（isRole 放宽）无法覆盖的内容都在此声明：首次进入的物品与冷却、
 * 进入/退出副作用、阻止切换的窗口、商店白名单、金钱可见性与任务收入来源。仅服务端钩子接收
 * ServerPlayerEntity；shopSpec、moneyVisible 与 taskReward 对每个职业恒定，双端读取。适配器从不触发
 * RoleAssigned、调用 addRole/initializeShop/setBalance，也不给物品打 CUSTOM_DATA 标记。
 */
public interface BlackRavenDisguiseAdapter {
    /** Exact acting role id; must be in {@link BlackRavenDisguiseRules#ALLOWED}. / 精确的扮演职业 id。 */
    Identifier roleId();

    /** Fresh stacks granted once, on the first entry of this round. / 本局首次进入时发放一次的新物品。 */
    default List<ItemStack> firstKit(ServerPlayerEntity player) {
        return List.of();
    }

    /** Round-start value of the NoellesRoles ability cooldown; the service aligns it. / 诺艾尔能力冷却的开局值，由服务端对齐。 */
    default int initialAbilityCooldownTicks() {
        return 0;
    }

    /** Witch skill slot while acting (e.g. death_omen); null leaves hasSkill() false. / 扮演期间的魔女技能槽；null 表示无技能。 */
    default @Nullable Identifier witchSkillId() {
        return null;
    }

    /** Round-start value of the witch skill cooldown; the service aligns it. / 魔女技能冷却开局值，由服务端对齐。 */
    default int initialWitchSkillCooldownTicks() {
        return 0;
    }

    /**
     * Role-owned first-entry state after the kit and the service-owned cooldowns (e.g. a component
     * cooldown via {@link FirstEntry#aligned(int)}, or a component reset).
     * 在物品与服务端冷却之后设置职业自有的首次进入状态（如组件冷却或重置）。
     */
    default void onFirstEntry(ServerPlayerEntity player, FirstEntry entry) {
    }

    /** Runs after the identity is live and indexed; forced resyncs go here. / 身份生效并入索引后运行；强制重同步放在这里。 */
    default void onEnter(ServerPlayerEntity player, boolean firstEntry) {
    }

    /** Runs before the identity stops being live, for every reason. / 身份失效前运行，适用于所有原因。 */
    default void onExit(ServerPlayerEntity player, DisguiseExitReason reason) {
    }

    /**
     * Non-null while a role window must refuse switching (diagnostic reason; the player sees window_active).
     * 职业窗口需要拒绝切换时返回非 null（诊断原因；玩家看到 window_active）。
     */
    default @Nullable String blocksSwitchReason(ServerPlayerEntity player) {
        return null;
    }

    default DisguiseShopSpec shopSpec() {
        return DisguiseShopSpec.EMPTY;
    }

    /** Money HUD for the disguised local player. / 伪装本地玩家的金钱显示。 */
    default boolean moneyVisible() {
        return false;
    }

    default DisguiseTaskReward taskReward() {
        return DisguiseTaskReward.NONE;
    }

    /** Round clock of a first entry. / 首次进入时的本局时钟。 */
    record FirstEntry(long roundStartAt, long now) {
        public int aligned(int initialTicks) {
            return BlackRavenDisguiseRules.aligned(initialTicks, roundStartAt, now);
        }
    }
}
