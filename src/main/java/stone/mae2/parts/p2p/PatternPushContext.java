package stone.mae2.parts.p2p;

import java.util.ArrayDeque;

/** Nested provider calls restore the caller's blocking flag even on exceptions. */
public final class PatternPushContext {
    private static final ThreadLocal<ArrayDeque<State>> FLAGS = ThreadLocal.withInitial(ArrayDeque::new);
    private PatternPushContext() {}
    private record State(boolean blocking, BlockingPolicy policy) {}
    public static boolean isBlocking() { var s = FLAGS.get().peek(); return s != null && s.blocking(); }
    public static BlockingPolicy policy() { var s = FLAGS.get().peek(); return s == null ? BlockingPolicy.DEFAULT : s.policy(); }
    public static void push(boolean value) { push(value, BlockingPolicy.DEFAULT); }
    public static void push(boolean value, BlockingPolicy policy) { FLAGS.get().push(new State(value, policy)); }
    public static void pop() {
        var flags = FLAGS.get();
        flags.pop();
        if (flags.isEmpty()) FLAGS.remove();
    }
}
