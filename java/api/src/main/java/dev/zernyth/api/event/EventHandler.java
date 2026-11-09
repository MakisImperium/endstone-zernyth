package dev.zernyth.api.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a synchronous native event handler: public instance void method with one supported event argument.
 * Exceptions are isolated by the bridge.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface EventHandler {

    /**
     * Selects the native handler priority.
     *
     * @return the native priority
     */
    EventPriority priority() default EventPriority.NORMAL;

    /**
     * Selects whether native dispatch skips already cancelled events.
     *
     * @return whether already cancelled events are skipped
     */
    boolean ignoreCancelled() default false;
}
