package dev.zernyth.api.world;

import java.util.List;
import java.util.Optional;

import dev.zernyth.api.entity.Entity;

/**
 * Endstone's current level. Native operations revalidate level identity on every access.
 */
public interface World {

    /**
     * Returns this object's declared or native name.
     *
     * @return the name
     */
    String getName();

    /**
     * Returns the native world or dimension identifier.
     *
     * @return the native identifier
     */
    String getIdentifier();

    /**
     * Queries the native level's dimensions.
     *
     * @return an unmodifiable list of dimension views
     */
    List<Dimension> getDimensions();

    /**
     * Resolves a named dimension or this block's dimension; a lookup returns empty when absent.
     *
     * @param identifier the native identifier scoped to its world/protocol
     * @return the dimension view, or an optional match for identifier lookups
     */
    Optional<Dimension> getDimension(String identifier);

    /**
     * Queries checked entity views from the native level.
     *
     * @return an unmodifiable list of checked entity views
     */
    List<Entity> getEntities();
}
