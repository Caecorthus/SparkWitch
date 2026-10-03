package dev.caecorthus.sparkwitch.compat.cooldown;

import dev.caecorthus.sparkfactionapi.api.cooldown.ItemCooldownNominalProvider;
import dev.caecorthus.sparkwitch.SparkWitchItems;
import dev.caecorthus.sparkwitch.compat.NoellesRoleIds;
import dev.caecorthus.sparkwitch.item.ceremonialsword.CeremonialSwordItem;
import dev.caecorthus.sparkwitch.item.firepoker.FirePokerRules;
import dev.caecorthus.sparkwitch.item.ninja.NinjaKnifeItem;
import dev.caecorthus.sparkwitch.item.ninja.NinjaShurikenItem;
import dev.caecorthus.sparkwitch.roles.civilian.controlexpert.ControlExpertRules;
import dev.caecorthus.sparkwitch.roles.civilian.fisher.FisherRules;
import dev.caecorthus.sparkwitch.roles.killer.bellringer.BellRingerRules;
import dev.caecorthus.sparkwitch.roles.killer.blackraven.BlackRavenRules;
import dev.caecorthus.sparkwitch.roles.killer.hunter.HunterRules;
import dev.caecorthus.sparkwitch.roles.killer.kidnapper.KidnapperRules;
import dev.caecorthus.sparkwitch.roles.killer.timestealer.TimeStealerRules;
import dev.caecorthus.sparkwitch.roles.neutral.insider.InsiderRules;
import dev.caecorthus.sparkwitch.roles.witch.abysslistener.AbyssListenerRules;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.item.AntidoteItem;
import org.agmas.noellesroles.item.PoisonNeedleItem;
import org.agmas.noellesroles.item.RepairToolItem;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Nominal (full post-use) cooldowns of the items whose cooldown SparkWitch writes, plus the public NoellesRoles item
 * constants, keyed by registry id so the provider works before and after item registration. Initial round-start
 * cooldowns are not nominals. Items without a cooldown (traps, shells, stamps, camera, Swordfish) are absent, the
 * Seeker car is exempt instead, and Wathe items fall through to Wathe's own table.
 * SparkWitch 写入其冷却的物品与 NoellesRoles 公开物品常量的标准（使用后完整）冷却，按注册 id 索引，物品注册前后均可用。
 * 开局冷却不算标准冷却。没有冷却的物品（陷阱、弹壳、邮票、摄像头、剑鱼）不在表内，搜寻者小车改为豁免，Wathe 物品回落到
 * Wathe 自身的表。
 */
final class SparkWitchItemCooldownNominals implements ItemCooldownNominalProvider {
    @Override
    public OptionalInt nominalTicks(Item item) {
        return nominalTicks(Registries.ITEM.getId(item));
    }

    static OptionalInt nominalTicks(Identifier itemId) {
        Integer ticks = itemId == null ? null : table().get(itemId);
        return ticks == null ? OptionalInt.empty() : OptionalInt.of(ticks);
    }

    static Map<Identifier, Integer> table() {
        return Table.ENTRIES;
    }

    private static Identifier noelles(String path) {
        return Identifier.of(NoellesRoleIds.NAMESPACE, path);
    }

    /** Lazy holder: the constant owners initialize on first lookup, not at registration. / 惰性持有者：首次查询时才初始化常量所有者。 */
    private static final class Table {
        static final Map<Identifier, Integer> ENTRIES = build();

        private static Map<Identifier, Integer> build() {
            Map<Identifier, Integer> entries = new LinkedHashMap<>();
            // Control Expert. / 控制专家。
            entries.put(SparkWitchItems.TASER_ID, ControlExpertRules.TASER_COOLDOWN);
            entries.put(SparkWitchItems.DISRUPTOR_ID, ControlExpertRules.DISRUPTOR_COOLDOWN);
            entries.put(SparkWitchItems.SHOCK_DEVICE_ID, ControlExpertRules.SHOCK_DEVICE_COOLDOWN);
            // Hunter: the empty-gun reload cooldown, not the 4-tick follow-up. / 猎人：打空后的装填冷却，而非 4 tick 连发间隔。
            entries.put(SparkWitchItems.DOUBLE_BARREL_SHOTGUN_ID, HunterRules.EMPTY_COOLDOWN_TICKS);
            // Time Stealer: display of the authoritative ClockReadyAt. / 窃时者：权威 ClockReadyAt 的显示冷却。
            entries.put(SparkWitchItems.TIME_STEALER_CLOCK_ID, TimeStealerRules.CLOCK_COOLDOWN_TICKS);
            entries.put(SparkWitchItems.TOLL_BELL_ID, BellRingerRules.TOLL_COOLDOWN_TICKS);
            // Angler: rod and the shared edible-fish use cooldown. / 钓鱼佬：鱼竿与共享的食鱼冷却。
            entries.put(SparkWitchItems.FISHING_ROD_ID, FisherRules.ROD_COOLDOWN_TICKS);
            entries.put(SparkWitchItems.SALMON_ID, FisherRules.FISH_USE_COOLDOWN_TICKS);
            entries.put(SparkWitchItems.COD_ID, FisherRules.FISH_USE_COOLDOWN_TICKS);
            entries.put(SparkWitchItems.CLOWNFISH_ID, FisherRules.FISH_USE_COOLDOWN_TICKS);
            entries.put(SparkWitchItems.GOLDFISH_ID, FisherRules.FISH_USE_COOLDOWN_TICKS);
            entries.put(SparkWitchItems.GLIMMERFISH_ID, FisherRules.FISH_USE_COOLDOWN_TICKS);
            // Ninja, Black Raven, Kidnapper. / 忍者、黑羽鸦、绑架者。
            entries.put(SparkWitchItems.NINJA_SHURIKEN_ID, NinjaShurikenItem.SHURIKEN_COOLDOWN_TICKS);
            entries.put(SparkWitchItems.NINJA_KNIFE_ID, NinjaKnifeItem.KNIFE_COOLDOWN_TICKS);
            entries.put(SparkWitchItems.FEATHER_BLADE_ID, BlackRavenRules.FEATHER_COOLDOWN_TICKS);
            entries.put(SparkWitchItems.KNOCKOUT_DRUG_ID, KidnapperRules.KNOCKOUT_DRUG_COOLDOWN_TICKS);
            // Grand Witch sword dash and the Murderous Witch Fire Poker. / 大魔女仪式剑冲刺与杀意魔女火钳。
            entries.put(SparkWitchItems.CEREMONIAL_SWORD_ID, CeremonialSwordItem.DASH_COOLDOWN_TICKS);
            entries.put(SparkWitchItems.FIRE_POKER_ID, FirePokerRules.COOLDOWN_TICKS);
            // Abyss Listener Shriek Gun: the post-shot cooldown, not the 60 s initial one. / 聆渊者啸音铳：开火后冷却，而非首次 60 秒。
            entries.put(SparkWitchItems.SHRIEK_GUN_ID, AbyssListenerRules.GUN_COOLDOWN_TICKS);
            // NoellesRoles items: public constants of the pinned 1.7.6 jar, plus the master key, which both
            // NoellesRoles (literal) and the Insider door path write as 200 ticks. The timed bomb stays exempt in
            // SparkFactionAPI itself (Bomber pass gate).
            // NoellesRoles 物品：锁定 1.7.6 jar 的公开常量；万能钥匙由 NoellesRoles（字面量）与内应开门路径都写为 200 tick。
            // 定时炸弹由 SparkFactionAPI 自身豁免（炸弹客转手门槛）。
            entries.put(noelles("antidote"), AntidoteItem.COOLDOWN_TICKS);
            entries.put(noelles("repair_tool"), RepairToolItem.COOLDOWN_TICKS);
            entries.put(noelles("poison_needle"), PoisonNeedleItem.USE_COOLDOWN_TICKS);
            entries.put(noelles("neutral_master_key"), InsiderRules.MASTER_KEY_COOLDOWN_TICKS);
            return Collections.unmodifiableMap(entries);
        }
    }
}
