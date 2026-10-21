package dev.caecorthus.sparkwitch.compat;

import java.lang.reflect.Method;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Version gate for SparkFactionAPI's settlement exclusion registry
 * ({@code dev.caecorthus.sparkfactionapi.api.settlement.SettlementExclusions}, 0.1.5.16+), through which SparkTraits
 * and SparkStrength learn that a role never counts as alive for round settlement. SparkWitch compiles against an older
 * SparkFactionAPI floor, so the registration is reflective and fails closed: an older SparkFactionAPI has no registry,
 * the call logs at debug and does nothing, and those mods keep counting the role (their old behaviour). SparkWitch's
 * own settlement seams never read this registry; they ask {@code BlackRavenSettlement} directly.
 * SparkFactionAPI 结算排除注册表（{@code dev.caecorthus.sparkfactionapi.api.settlement.SettlementExclusions}，0.1.5.16+）
 * 的版本开关：SparkTraits 与 SparkStrength 通过它得知某职业在回合结算中永不算存活。SparkWitch 以较旧的 SparkFactionAPI
 * 下限编译，因此注册采用反射并失败关闭：旧版 SparkFactionAPI 没有该注册表时，只记录 debug 日志、不做任何事，这些模组
 * 照常计入该职业（旧行为）。SparkWitch 自身的结算接缝从不读取该注册表，而是直接询问 {@code BlackRavenSettlement}。
 */
public final class SparkFactionSettlementExclusionCompat {
    static final String REGISTRY_CLASS = "dev.caecorthus.sparkfactionapi.api.settlement.SettlementExclusions";
    static final String REGISTER_METHOD = "registerRole";
    private static final Logger LOGGER = LoggerFactory.getLogger(SparkFactionSettlementExclusionCompat.class);

    private SparkFactionSettlementExclusionCompat() {
    }

    /**
     * Registers {@code roleId} as excluded from settlement when SparkFactionAPI provides the registry. Returns true
     * only when the reflective call succeeded; never throws.
     * 当 SparkFactionAPI 提供注册表时，将 {@code roleId} 注册为结算排除职业。仅在反射调用成功时返回 true；从不抛出异常。
     */
    public static boolean registerExcludedRole(Identifier roleId) {
        if (roleId == null) {
            return false;
        }
        try {
            Class<?> registry = Class.forName(REGISTRY_CLASS, true,
                    SparkFactionSettlementExclusionCompat.class.getClassLoader());
            Method register = registry.getMethod(REGISTER_METHOD, Identifier.class);
            register.invoke(null, roleId);
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException missing) {
            LOGGER.debug("SparkFactionAPI has no settlement exclusion registry; {} keeps counting for other mods", roleId);
            return false;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            LOGGER.debug("SparkFactionAPI settlement exclusion registration failed for {}", roleId, failure);
            return false;
        }
    }
}
