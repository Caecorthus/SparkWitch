package dev.caecorthus.sparkwitch.api.client;

import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityController;
import dev.caecorthus.sparkwitch.client.ability.SecondaryAbilityRegistry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Objects;

/**
 * Stable cross-mod contract (client only): lets another mod put a role's action on SparkWitch's Role Skill 2 key
 * (default N). SparkStrength reaches it by reflection, so this FQCN and both signatures must not change. A press
 * reaches the handler only while the local player's raw Wathe role id equals {@code roleId} on a confirmed SparkWitch
 * server; it is a client-side trigger only, and the caller's server must validate whatever the handler sends.
 * 稳定跨模组契约（仅客户端）：允许其他模组把职业行为挂到 SparkWitch 的“职业技能 2”键（默认 N）。SparkStrength 通过反射
 * 访问本类，因此完整类名与两个方法签名都不得更改。仅当本地玩家的原始 Wathe 职业 id 等于 {@code roleId} 且服务器已确认
 * 运行 SparkWitch 时按键才会派发；这只是客户端触发，处理器发出的任何请求都必须由调用方的服务端校验。
 */
public final class SecondarySkillKeyApi {
    private SecondarySkillKeyApi() {
    }

    /**
     * Registers {@code onPressed} for {@code roleId}; it runs on the client thread. Returns false, without throwing,
     * when the role already has a handler (the first one stays). Null arguments throw {@link NullPointerException}.
     * 为 {@code roleId} 注册 {@code onPressed}，在客户端线程运行。该职业已有处理器时返回 false 且不抛异常（保留先注册者）；
     * 参数为 null 时抛出 {@link NullPointerException}。
     */
    public static boolean register(Identifier roleId, Runnable onPressed) {
        Objects.requireNonNull(roleId, "roleId");
        Objects.requireNonNull(onPressed, "onPressed");
        return SecondaryAbilityRegistry.tryRegister(roleId, client -> onPressed.run());
    }

    /**
     * Localized name of the key bound to Role Skill 2 (e.g. "N"); the key's own name before it is registered.
     * “职业技能 2”当前绑定按键的本地化名称（如 “N”）；按键注册前返回该按键本身的名称。
     */
    public static Text boundKeyText() {
        return SecondaryAbilityController.secondaryKeyText();
    }
}
