package dev.zernyth.runtime;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import dev.zernyth.api.network.NetworkProtocol;
import dev.zernyth.api.network.PacketContext;
import dev.zernyth.api.network.PacketDirection;
import dev.zernyth.api.network.PacketFilter;
import dev.zernyth.api.network.PacketSnapshot;
import dev.zernyth.api.network.PacketType;

final class NetworkValueTest {

    private static void invalid(Runnable run) {
        try {
            run.run();
            throw new AssertionError("Invalid packet value accepted");
        } catch (IllegalArgumentException expected) {
        }
    }

    static void run() {
        invalid(() -> new PacketType("invalid", -1, 2225));
        invalid(() -> new PacketType("invalid", 1024, 2225));
        invalid(() -> new PacketFilter(Set.of(), Set.of()));
        var source = new HashSet<PacketDirection>();
        source.add(PacketDirection.INBOUND);
        var filter = new PacketFilter(Set.of(), source);
        source.clear();
        if (filter.directions().size() != 1) {
            throw new AssertionError("Aliased filter");
        }
        var type = new PacketType("UNKNOWN", 1023, 2225);
        var context = new PacketContext(Optional.empty(), Optional.empty(), Instant.EPOCH,
                PacketContext.Phase.INBOUND_BEFORE_NATIVE_DESERIALIZATION, 0, false);
        invalid(()
                -> new PacketSnapshot(
                type, PacketDirection.INBOUND, new NetworkProtocol("other", 1), context, 0));
        invalid(()
                -> new PacketSnapshot(type, PacketDirection.INBOUND,
                new NetworkProtocol("26.60", 2225), context, -1));
        System.out.println("Network value/filter/version checks passed (no BDS/JNI).");
    }
}
