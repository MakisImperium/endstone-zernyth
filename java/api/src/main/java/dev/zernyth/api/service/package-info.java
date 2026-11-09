/**
 * Native Endstone service registry adapters. All registration, lookup and service
 * invocations require the server thread, enabled plugin and compatible public
 * shared interface. Invalid Java arguments throw IllegalArgumentException or
 * NullPointerException; stale providers, class-loader mismatches and native/thread
 * failures throw IllegalStateException. find/load handle absent providers explicitly.
 * Returned proxies are revocable leases, not provider implementation references.
 * Embedded Java and class-loader isolation do not provide a security sandbox.
 */
package dev.zernyth.api.service;
