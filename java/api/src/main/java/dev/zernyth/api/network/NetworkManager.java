package dev.zernyth.api.network;

import java.util.List;
import java.util.Set;

import dev.zernyth.api.event.EventPriority;
import dev.zernyth.api.event.Registration;

/**
 * Plugin-owned native metadata observation. Live operations require the server thread.
 */
public interface NetworkManager {

    /**
     * Queries the native-reported release and protocol, not a full BDS build identity.
     *
     * @return the native-reported release/protocol
     */
    NetworkProtocol getProtocol();

    /**
     * Queries capabilities actually supported by the current native/version adapter.
     *
     * @return the actually supported capabilities
     */
    Set<NetworkCapability> getCapabilities();

    /**
     * Named native IDs, scoped to getProtocol(); all other IDs remain UNKNOWN metadata.
     *
     * @return known native types, empty without a compatible adapter
     */
    List<PacketType> getPacketTypes();

    /**
     * Registers a synchronous metadata observer with filtering before JNI. Packet mutation/cancellation is
     * unavailable.
     *
     * @param filter the native packet type/direction filter
     * @param priority the native handler or service provider priority
     * @param listener the handler object, non-null
     * @return the plugin-owned listener registration
     */
    Registration listen(PacketFilter filter, EventPriority priority, PacketListener listener);

    /**
     * Queries lifetime-checked player-associated views, not raw transport sessions.
     *
     * @return the current player-associated views
     */
    List<NetworkConnection> getConnections();

    /**
     * Enables or disables additional packet timing measurements.
     *
     * @param enabled whether timing diagnostics should be collected
     */
    void setDiagnosticsEnabled(boolean enabled);

    /**
     * Reads cumulative native/Java packet delivery measurements.
     *
     * @return detached cumulative metrics
     */
    NetworkMetrics getMetrics();
}
