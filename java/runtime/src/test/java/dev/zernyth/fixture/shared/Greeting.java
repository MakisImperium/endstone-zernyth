package dev.zernyth.fixture.shared;

/**
 * Shared service identity used by producer/consumer classloader and revocation tests.
 */
public interface Greeting {

    /**
     * Supplies the provider's test greeting; a registry proxy enforces native owner checks.
     *
     * @return the provider's greeting value
     */
    String greet();
}
