package io.github.kernelperson.camera;

import java.util.HashMap;
import java.util.Map;

/** Main-thread-only admission and identity check; a stale callback cannot finish newer work. */
final class WorkGate<K> {
    private record Entry(long token, long started, boolean busy) {}
    private final Map<K, Entry> entries = new HashMap<>();
    private final int maximum;
    private final long cooldown;
    private long sequence;
    WorkGate(int maximum, long cooldown) {
        if (maximum < 1 || cooldown < 0) throw new IllegalArgumentException();
        this.maximum = maximum; this.cooldown = cooldown;
    }
    long begin(K key, long now) {
        entries.entrySet().removeIf(e -> !e.getValue().busy && now - e.getValue().started >= cooldown);
        if (entries.containsKey(key) || entries.values().stream().filter(Entry::busy).count() >= maximum) return 0;
        long token = ++sequence;
        entries.put(key, new Entry(token, now, true));
        return token;
    }
    boolean current(K key, long token) {
        Entry entry = entries.get(key);
        return entry != null && entry.busy && entry.token == token;
    }
    void finish(K key, long token) {
        if (current(key, token)) {
            Entry old = entries.get(key);
            entries.put(key, new Entry(token, old.started, false));
        }
    }
    void invalidate(K key) { entries.remove(key); }
    void clear() { entries.clear(); }
}
