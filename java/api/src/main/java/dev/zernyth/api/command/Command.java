package dev.zernyth.api.command;

/**
 * Closing unregisters this command from Endstone. Server thread only.
 */
public interface Command extends AutoCloseable {

    /**
     * Returns this object's declared or native name.
     *
     * @return the name
     */
    String getName();

    /**
     * Releases the registration/state under its server-thread and owner lifetime contract.
     */
    @Override
    void close();
}
