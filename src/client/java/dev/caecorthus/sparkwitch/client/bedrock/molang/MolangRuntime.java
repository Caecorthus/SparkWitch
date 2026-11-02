package dev.caecorthus.sparkwitch.client.bedrock.molang;

/**
 * Per-model Molang state: query values set by the caller each frame (canonical lower-case names without the
 * {@code query.}/{@code q.} prefix, e.g. {@code anim_time}), persistent {@code variable.*} values, and per-evaluation
 * {@code temp.*} values. Unknown names read as 0.
 * 每个模型各自的 Molang 状态：调用方每帧设置的查询值（规范的小写名字，不含 {@code query.}/{@code q.} 前缀，例如
 * {@code anim_time}）、持久的 {@code variable.*} 值，以及每次求值的 {@code temp.*} 值。未知名字读作 0。
 */
public final class MolangRuntime {
    // CONTRACT SKELETON (2026-11-02): the animation agent owns the implementation.

    public void setQuery(String name, double value) {
        throw new UnsupportedOperationException("TODO");
    }

    public double query(String name) {
        throw new UnsupportedOperationException("TODO");
    }

    public double variable(String name) {
        throw new UnsupportedOperationException("TODO");
    }

    public void setVariable(String name, double value) {
        throw new UnsupportedOperationException("TODO");
    }
}
