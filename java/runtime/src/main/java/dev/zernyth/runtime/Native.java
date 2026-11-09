package dev.zernyth.runtime;

/**
 * Private production boundary. All handles are opaque registry IDs, never addresses.
 */
final class Native {

    static native void translation(long owner, long subject, boolean sender, String key, String[] arguments);

    static native long[] performance(long owner, int enabled);

    static native void validate(long owner);

    static native String[] ecosystemInfo(long owner);

    static native long[] ecosystemCounts(long owner);

    static native long serviceRegister(long owner, String name, Class<?> type, Object provider, int priority);

    static native void serviceRemove(long owner, long token);

    static native long[] serviceFind(long owner, String name, Class<?> type);

    static native Object serviceProvider(long owner, long token);

    interface PacketCallback {

        void accept(int id, int direction, long observedMillis, long size, int subClient, boolean cancelled,
                String player, String connection);
    }

    static native String[] networkInfo(long owner);

    static native long networkListen(
            long owner, int directions, int[] ids, int priority, PacketCallback callback);

    static native void networkRemove(long owner, long listener);

    static native long[] networkMetrics(long owner, int diagnostics);

    static native String[] networkConnections(long owner);

    static native boolean networkActive(long owner, String connection);

    interface FormCallback {

        void complete(byte[] response);
    }

    static native byte[] advanced(long plugin, int operation, long handle, byte[] request);

    static native long showForm(long plugin, long player, byte[] form, FormCallback callback);

    static native void cancelForm(long plugin, long request);

    interface EventCallback {

        void accept(long event, long player);
    }

    interface CommandCallback {

        boolean execute(long sender, String[] args);
    }

    static native long[] players(long plugin);

    static native String[] playerData(long plugin, long player, int kind);

    static native int playerNumber(long plugin, long player, int kind);

    static native boolean playerAction(long plugin, long player, int kind, String text, double[] values);

    static native boolean permission(long plugin, long subject, String permission, boolean sender);

    static native long subscribe(
            long plugin, String event, int priority, boolean ignore, EventCallback callback);

    static native void unsubscribe(long plugin, long registration);

    static native boolean cancellation(long plugin, long event, boolean write, boolean value);

    static native String eventText(long plugin, long event, boolean write, String value);

    static native String[] eventData(long plugin, long event, int kind);

    static native long command(
            long plugin, String name, String description, String permission, CommandCallback callback);

    static native void removeCommand(long plugin, long command);

    static native String[] senderData(long plugin, long sender);

    static native void senderMessage(long plugin, long sender, String message);

    static native long schedule(long plugin, Runnable callback, long delay, long period, boolean async);

    static native boolean task(long plugin, long task, boolean cancel);

    static native void cancelTasks(long plugin);
}
