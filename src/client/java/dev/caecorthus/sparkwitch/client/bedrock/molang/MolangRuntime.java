package dev.caecorthus.sparkwitch.client.bedrock.molang;

import java.util.HashMap;
import java.util.Locale;

/**
 * Per-model Molang state: query values set by the caller each frame (canonical lower-case names without the
 * {@code query.}/{@code q.} prefix, e.g. {@code anim_time}), persistent {@code variable.*} values, and per-evaluation
 * {@code temp.*} values. Unknown names read as 0.
 * 每个模型各自的 Molang 状态：调用方每帧设置的查询值（规范的小写名字，不含 {@code query.}/{@code q.} 前缀，例如
 * {@code anim_time}）、持久的 {@code variable.*} 值，以及每次求值的 {@code temp.*} 值。未知名字读作 0。
 *
 * <p>Not thread-safe: one runtime belongs to one model on one thread (the render thread). Names are scoped to this
 * runtime: there is no process-wide name table, so no file can exhaust names for later skins. Each kind keeps at most
 * {@link #MAX_NAMES_PER_KIND} names; writing one more forgets that kind's older names first, so a hostile file can
 * only reset its own state, never block a later skin, and memory stays bounded. A slot is created the first time a
 * name is written; after that, reads and writes are a {@code HashMap} lookup and never allocate. Temps are cleared in
 * O(1) at the start of every top-level evaluation by bumping a generation stamp.
 * 非线程安全：一个运行时只属于一个模型、一个线程（渲染线程）。名字的作用域限于本运行时：不存在进程级名字表，任何文件都无法
 * 耗尽后续皮肤可用的名字。每类最多保留 {@link #MAX_NAMES_PER_KIND} 个名字；再写入新名字时先清空该类的旧名字，因此恶意文件
 * 只能重置自身状态，不会阻塞后续皮肤，内存也有上限。名字首次写入时创建槽位，之后读写只是一次 HashMap 查找，不分配内存。
 * 每次顶层求值开始时通过递增代号以 O(1) 清空临时变量。</p>
 */
public final class MolangRuntime {
    /** Distinct names kept per kind (queries, variables, temps). / 每类（查询、变量、临时变量）保留的名字上限。 */
    public static final int MAX_NAMES_PER_KIND = 4096;

    private final HashMap<String, Slot> queries = new HashMap<>();
    private final HashMap<String, Slot> variables = new HashMap<>();
    private final HashMap<String, Slot> temps = new HashMap<>();
    private int tempGeneration = 1;
    private long randomState = mixSeed(System.nanoTime());

    /** Set by a {@code return} statement; read by statement lists. / 由 return 语句设置，语句序列读取。 */
    boolean returning;
    double returnValue;

    /** A name's value; it exists once the name was written. / 名字的值；名字被写入后才存在。 */
    private static final class Slot {
        double value;
        /** Temps only: the evaluation that wrote it. / 仅临时变量：写入它的那次求值。 */
        int generation;
    }

    public void setQuery(String name, double value) {
        slot(queries, canonical(name, "query.", "q.")).value = value;
    }

    public double query(String name) {
        return queryValue(canonical(name, "query.", "q."));
    }

    public double variable(String name) {
        return variableValue(canonical(name, "variable.", "v."));
    }

    public void setVariable(String name, double value) {
        storeVariable(canonical(name, "variable.", "v."), value);
    }

    /** Forgets every {@code variable.*} (e.g. on a skin change). / 清除所有 variable.*（例如切换皮肤时）。 */
    public void clearVariables() {
        variables.clear();
    }

    /** Makes {@code math.random*}/{@code math.die_roll*} repeatable. / 使随机函数可复现。 */
    public void setRandomSeed(long seed) {
        randomState = mixSeed(seed);
    }

    // ---- package-private fast paths for compiled nodes; names are canonical / 编译节点使用的包内快速路径 ----

    double queryValue(String name) {
        Slot slot = queries.get(name);
        return slot == null ? 0.0 : slot.value;
    }

    boolean hasQuery(String name) {
        return queries.get(name) != null;
    }

    double variableValue(String name) {
        Slot slot = variables.get(name);
        return slot == null ? 0.0 : slot.value;
    }

    boolean hasVariable(String name) {
        return variables.get(name) != null;
    }

    void storeVariable(String name, double value) {
        slot(variables, name).value = value;
    }

    double tempValue(String name) {
        Slot slot = temps.get(name);
        return slot != null && slot.generation == tempGeneration ? slot.value : 0.0;
    }

    boolean hasTemp(String name) {
        Slot slot = temps.get(name);
        return slot != null && slot.generation == tempGeneration;
    }

    void storeTemp(String name, double value) {
        Slot slot = slot(temps, name);
        slot.value = value;
        slot.generation = tempGeneration;
    }

    /** Starts one top-level evaluation: clears temps and the return flag. / 开始一次顶层求值：清空临时变量与返回标记。 */
    void beginEvaluation() {
        if (++tempGeneration == 0) {
            temps.clear();
            tempGeneration = 1;
        }
        returning = false;
        returnValue = 0.0;
    }

    /** Uniform in [0, 1); xorshift64*, allocation-free. / [0, 1) 均匀分布；xorshift64*，不分配内存。 */
    double nextRandom() {
        long x = randomState;
        x ^= x >>> 12;
        x ^= x << 25;
        x ^= x >>> 27;
        randomState = x;
        return ((x * 0x2545F4914F6CDD1DL) >>> 11) * 0x1.0p-53;
    }

    /** The slot for {@code name}, created on first write; a full kind is cleared first. / 首次写入时创建槽位。 */
    private static Slot slot(HashMap<String, Slot> kind, String name) {
        Slot slot = kind.get(name);
        if (slot == null) {
            if (kind.size() >= MAX_NAMES_PER_KIND) {
                kind.clear();
            }
            slot = new Slot();
            kind.put(name, slot);
        }
        return slot;
    }

    private static long mixSeed(long seed) {
        long z = seed + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return z == 0L ? 0x9E3779B97F4A7C15L : z;
    }

    /** Lower-cases (no copy when already lower-case) and drops a long or short namespace prefix. */
    private static String canonical(String name, String longPrefix, String shortPrefix) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.startsWith(longPrefix)) {
            return lower.substring(longPrefix.length());
        }
        if (lower.startsWith(shortPrefix)) {
            return lower.substring(shortPrefix.length());
        }
        return lower;
    }
}
