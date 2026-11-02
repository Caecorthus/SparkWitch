package dev.caecorthus.sparkwitch.client.bedrock.molang;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-wide name to slot table. Compiled expressions keep slot numbers and {@link MolangRuntime} keeps arrays, so
 * evaluation indexes arrays instead of hashing strings. Query, variable and temp names share one id space (each kind
 * has its own value array). Compilation may run off the render thread, hence the concurrent map.
 * 进程级的名字到槽位表。编译后的表达式保存槽位号，{@link MolangRuntime} 保存数组，求值时按下标访问而不是哈希字符串。查询、
 * 变量和临时变量共用一个编号空间（各自有独立的值数组）。编译可能在非渲染线程进行，因此使用并发映射。
 */
final class MolangNames {
    /** Bounds the runtime arrays against hostile files. / 限制运行时数组大小，防御恶意文件。 */
    static final int MAX_NAMES = 1 << 16;

    private static final ConcurrentHashMap<String, Integer> IDS = new ConcurrentHashMap<>();
    private static int next;

    private MolangNames() {
    }

    /** The slot for {@code name}, creating it; -1 once the table is full. */
    static int intern(String name) {
        Integer id = IDS.get(name);
        if (id != null) {
            return id;
        }
        synchronized (IDS) {
            id = IDS.get(name);
            if (id != null) {
                return id;
            }
            if (next >= MAX_NAMES) {
                return -1;
            }
            int created = next++;
            IDS.put(name, created);
            return created;
        }
    }

    /** The slot for {@code name}, or -1 when nothing ever used it. Never allocates. */
    static int find(String name) {
        Integer id = IDS.get(name);
        return id == null ? -1 : id;
    }
}
