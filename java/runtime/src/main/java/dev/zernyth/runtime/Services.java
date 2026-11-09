package dev.zernyth.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import dev.zernyth.api.event.Registration;
import dev.zernyth.api.service.ServicePriority;
import dev.zernyth.api.service.ServicesManager;

final class Services implements ServicesManager {

    private final long owner;

    Services(long owner) {
        this.owner = owner;
    }

    private static void type(Class<?> type) {
        if (!type.isInterface() || !Modifier.isPublic(type.getModifiers())) {
            throw new IllegalArgumentException("Service type must be a public shared interface");
        }
    }

    public <T> Registration register(Class<T> type, T provider, ServicePriority priority) {
        type(type);
        Objects.requireNonNull(provider);
        Objects.requireNonNull(priority);
        if (!type.isInstance(provider)) {
            throw new IllegalArgumentException("Provider does not implement service interface");
        }
        long token = Native.serviceRegister(owner, type.getName(), type, provider, priority.ordinal());
        return () -> Native.serviceRemove(owner, token);
    }

    private <T> T proxy(Class<T> type, long token) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> "ZernythService[" + type.getName() + "]";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> throw new IllegalStateException();
                        };
                    }
                    Object provider = Native.serviceProvider(owner, token);
                    try {
                        return method.invoke(provider, args);
                    } catch (InvocationTargetException error) {
                        throw error.getCause();
                    }
                }));
    }

    public <T> Optional<T> find(Class<T> type) {
        type(type);
        long[] tokens = Native.serviceFind(owner, type.getName(), type);
        return tokens.length == 0 ? Optional.empty() : Optional.of(proxy(type, tokens[0]));
    }

    public <T> List<T> getProviders(Class<T> type) {
        type(type);
        var result = new ArrayList<T>();
        for (long token : Native.serviceFind(owner, type.getName(), type)) {
            result.add(proxy(type, token));
        }
        return List.copyOf(result);
    }
}
