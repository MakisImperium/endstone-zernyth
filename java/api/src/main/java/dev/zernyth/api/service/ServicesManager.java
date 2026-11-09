package dev.zernyth.api.service;

import java.util.List;
import java.util.Optional;

import dev.zernyth.api.event.Registration;

/**
 * Server-thread-only, plugin-owned providers. Service types must be shared public interfaces.
 * Proxies revalidate caller/provider at every invocation and fail after provider revocation.
 */
public interface ServicesManager {

    /**
     * Registers a provider in the native service manager under this plugin generation.
     *
     * @param <T> the public shared service interface
     * @param service the public shared service interface class
     * @param provider the implementation of the public shared service interface
     * @param priority the native handler or service provider priority
     * @return a registration that revokes this provider on close
     * @throws IllegalArgumentException if the type is not a public interface or the provider does not
     * implement it
     * @throws IllegalStateException if native ownership or thread validation fails
     */
    <T> Registration register(Class<T> service, T provider, ServicePriority priority);

    /**
     * Resolves the preferred native service as a revocable proxy, or empty when absent.
     *
     * @param <T> the public shared service interface
     * @param service the public shared service interface class
     * @return the preferred provider proxy, or empty if unavailable
     */
    <T> Optional<T> find(Class<T> service);

    /**
     * Resolves the preferred service, returning null if none is available. A returned proxy
     * revalidates consumer and provider generation on every service invocation.
     *
     * @param <T> the public shared service interface
     * @param service the public shared service interface class
     * @return the preferred revocable proxy, or null if unavailable
     */
    default <T> T load(Class<T> service) {
        return find(service).orElse(null);
    }

    /**
     * Resolves native providers in native priority order as revocable proxies.
     *
     * @param <T> the public shared service interface
     * @param service the public shared service interface class
     * @return the ordered provider proxies
     */
    <T> List<T> getProviders(Class<T> service);
}
