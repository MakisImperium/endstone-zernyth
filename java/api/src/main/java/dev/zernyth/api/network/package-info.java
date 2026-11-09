/**
 * Java 25 LTS read-only native packet metadata. Registration, removal, connection
 * queries and metrics require the server thread and a live enabled plugin.
 * Invalid handles/threads/native errors throw IllegalStateException; invalid Java
 * values throw IllegalArgumentException/NullPointerException. Unsupported versions
 * throw UnsupportedPacketException before registration. Snapshots are immutable
 * and may be retained and read on workers; live BDS views must not be used there.
 * Existing EventPriority and Registration are reused. No raw packet data, transport
 * secrets, cancellation or modification are exposed. No asynchronous listener queue
 * is implemented; ASYNC_OBSERVATION is absent from the capability set.
 * Embedded Java plugins are trusted code: this API is not a security sandbox.
 */
package dev.zernyth.api.network;
