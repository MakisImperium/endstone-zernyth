package dev.zernyth.api.network;

import java.util.Objects;

/**
 * Native reported release, not proof of the full BDS build or the actual transport in use.
 *
 * @param reportedRelease the native-reported release string
 * @param protocolVersion the protocol number, not assumed portable across versions
 */
public record NetworkProtocol(String reportedRelease, int protocolVersion) {

    /**
     * Requires a reported release string; the numeric protocol alone does not establish adapter support.
     *
     * @param reportedRelease the native-reported release string
     * @param protocolVersion the protocol number, not assumed portable across versions
     */
    public NetworkProtocol {
        Objects.requireNonNull(reportedRelease);
    }
}
