package dev.caecorthus.sparkwitch.roles.killer.blackraven.disguise.adapter;

import java.util.List;

/**
 * Registers the shipped disguise adapters once during common init on both sides.
 * 在双端通用初始化时一次性注册已上线的伪装适配器。
 */
public final class BlackRavenDisguiseAdapterBootstrap {
    private static boolean registered;

    private BlackRavenDisguiseAdapterBootstrap() {
    }

    public static synchronized void registerBatchOne() {
        if (registered) {
            return;
        }
        registered = true;
        for (BlackRavenDisguiseAdapter adapter : batchOne()) {
            BlackRavenDisguiseAdapters.register(adapter);
        }
    }

    /** Fresh batch-1 adapters in BATCH1_SHIPPED order. / 按 BATCH1_SHIPPED 顺序创建的批次一适配器。 */
    static List<BlackRavenDisguiseAdapter> batchOne() {
        return List.of(
                new ConductorDisguiseAdapter(),
                new AttendantDisguiseAdapter(),
                new AwesomeBinglusDisguiseAdapter(),
                new MermaidDisguiseAdapter(),
                new TimeKeeperDisguiseAdapter(),
                new WaiterDisguiseAdapter(),
                new ReporterDisguiseAdapter(),
                new TarotReaderDisguiseAdapter(),
                new OrthopedistDisguiseAdapter()
        );
    }
}
