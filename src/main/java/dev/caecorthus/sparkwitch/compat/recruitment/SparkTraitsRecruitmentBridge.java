package dev.caecorthus.sparkwitch.compat.recruitment;

import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/** Optional SparkTraits public-facade calls for recruitment (the charisma price wrapper, the Depression psycho target
 * gate and the recruit-role trait swap), never naming or reflecting any Traits internals.
 * 招募用的可选 SparkTraits 公共门面调用（魅力价格包装、抑郁狂暴目标判定与新身份词条替换），不命名或反射任何 Traits 内部实现；
 * 缺少门面时保守退化。 */
final class SparkTraitsRecruitmentBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger(SparkTraitsRecruitmentBridge.class);
    private static final String API = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    private static final @Nullable Class<?> API_CLASS = resolveApi();
    private static final @Nullable Method DISCOUNT = resolve("discountShopEntryForCharisma",
            PlayerEntity.class, ShopEntry.class);
    private static final @Nullable Method REPLACE_INELIGIBLE = resolve("replaceTraitsIneligibleForCurrentRole",
            ServerPlayerEntity.class, BiConsumer.class);
    private static final @Nullable Method DEPRESSION_PSYCHO = resolve("isDepressionPsychoActive", PlayerEntity.class);
    private static final AtomicBoolean REPLACE_MISSING_WARNED = new AtomicBoolean();
    private static final AtomicBoolean PSYCHO_MISSING_WARNED = new AtomicBoolean();
    private static final AtomicBoolean PSYCHO_FAILURE_WARNED = new AtomicBoolean();

    private SparkTraitsRecruitmentBridge() { }

    static @Nullable Class<?> charismaWrapperType(PlayerEntity player) {
        if (DISCOUNT == null) return null;
        try {
            ShopEntry probe = new ShopEntry(ItemStack.EMPTY, 100, ShopEntry.Type.TOOL);
            Object discounted = DISCOUNT.invoke(null, player, probe);
            return discounted instanceof ShopEntry entry && entry != probe ? entry.getClass() : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return null;
        }
    }

    /**
     * Whether SparkTraits Depression psycho (fake death, real inventory stashed by SparkTraits) is active. An absent
     * or older provider answers false, as before the gate existed; a throwing or malformed answer refuses (true). It
     * runs on every recruit attempt, so each warning is logged at most once and nothing is thrown.
     * SparkTraits 抑郁狂暴（假死，真实背包由 SparkTraits 暂存）是否生效。提供方缺失或过旧时为 false，与该判定出现前一致；
     * 提供方抛异常或返回非 Boolean 时保守拒绝（true）。每次招募尝试都会调用，因此每类警告最多记录一次且绝不抛出。
     */
    static boolean isDepressionPsychoActive(@Nullable PlayerEntity player) {
        if (DEPRESSION_PSYCHO == null) {
            if (API_CLASS != null && PSYCHO_MISSING_WARNED.compareAndSet(false, true)) {
                LOGGER.warn("SparkTraits has no isDepressionPsychoActive; Depression psycho targets stay recruitable");
            }
            return false;
        }
        if (player == null) return false;
        try {
            if (DEPRESSION_PSYCHO.invoke(null, player) instanceof Boolean active) return active;
            warnPsychoFailureOnce(null);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            warnPsychoFailureOnce(exception);
        }
        return true;
    }

    /**
     * Asks the facade to swap every trait the recruit's already-assigned role could not have rolled for as many
     * redraws; the provider does the removal, the draws and the sync itself. Fails closed to "keep traits": an absent
     * or older provider changes nothing; a failing one is logged. Either way the result is {@link RecruitmentTraitChange#NONE}
     * and nothing is thrown. Only the owner-visible names come back, filtered to {@code Text}.
     * 请门面把新身份开局无法抽到的全部词条替换为同等数量的补抽词条，移除、补抽与同步由提供方完成。保守退化为“保留词条”：
     * 提供方缺失或过旧时不做任何改动；提供方出错时记录日志。两种情况都返回 {@link RecruitmentTraitChange#NONE} 且绝不抛出。
     * 只返回拥有者可见的词条名，并只保留 {@code Text}。
     */
    static RecruitmentTraitChange replaceTraitsIneligibleForRecruitRole(@Nullable ServerPlayerEntity recruit) {
        if (REPLACE_INELIGIBLE == null) {
            if (API_CLASS != null && REPLACE_MISSING_WARNED.compareAndSet(false, true)) {
                LOGGER.warn("SparkTraits has no replaceTraitsIneligibleForCurrentRole; recruits keep every trait");
            }
            return RecruitmentTraitChange.NONE;
        }
        if (recruit == null) return RecruitmentTraitChange.NONE;
        AtomicReference<RecruitmentTraitChange> change = new AtomicReference<>(RecruitmentTraitChange.NONE);
        BiConsumer<Object, Object> visitor = (lost, gained) -> change.set(new RecruitmentTraitChange(texts(lost), texts(gained)));
        try {
            REPLACE_INELIGIBLE.invoke(null, recruit, visitor);
            return change.get();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            LOGGER.warn("SparkTraits failed to replace ineligible traits for recruit {}", recruit.getUuid(), exception);
            return RecruitmentTraitChange.NONE;
        }
    }

    private static List<Text> texts(Object names) {
        if (!(names instanceof List<?> list)) return List.of();
        List<Text> texts = new ArrayList<>(list.size());
        for (Object name : list) {
            if (name instanceof Text text) texts.add(text);
        }
        return texts;
    }

    private static void warnPsychoFailureOnce(@Nullable Throwable failure) {
        if (PSYCHO_FAILURE_WARNED.compareAndSet(false, true)) {
            LOGGER.warn("SparkTraits isDepressionPsychoActive failed; refusing recruitment of that target", failure);
        }
    }

    private static @Nullable Class<?> resolveApi() {
        try {
            return Class.forName(API);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return null;
        }
    }

    private static @Nullable Method resolve(String name, Class<?>... parameters) {
        if (API_CLASS == null) return null;
        try {
            Method method = API_CLASS.getMethod(name, parameters);
            return Modifier.isStatic(method.getModifiers()) ? method : null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return null;
        }
    }
}
