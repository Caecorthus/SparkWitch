package dev.caecorthus.sparkwitch.roles.killer.bellringer;

import java.util.function.BooleanSupplier;

/**
 * Side-neutral glint seam: the client installs a supplier reading only the local player's owner-synced
 * toll flag, so another viewer's client never learns whether the ringer has targets. Servers keep the
 * default and always answer false.
 * 与端无关的附魔光效接口：客户端安装只读取本地玩家“仅拥有者同步”敲钟标记的提供器，
 * 因此其他观察者的客户端无法得知敲钟人是否有目标。服务端保持默认值，始终返回 false。
 */
public final class TollBellGlint {
    private static final BooleanSupplier UNLIT = () -> false;
    private static volatile BooleanSupplier supplier = UNLIT;

    private TollBellGlint() {
    }

    public static void install(BooleanSupplier glintSupplier) {
        supplier = glintSupplier == null ? UNLIT : glintSupplier;
    }

    public static boolean isLit() {
        try {
            return supplier.getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
