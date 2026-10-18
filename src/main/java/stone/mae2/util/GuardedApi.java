package stone.mae2.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.function.BooleanSupplier;

/** Held capability objects must stop working after their endpoint is removed or rebound. */
public final class GuardedApi {
    private GuardedApi() {}
    public static <T> T create(Class<T> type, T active, T empty, BooleanSupplier valid) {
        return create(type, active, empty, valid, null);
    }
    public static <T> T create(Class<T> type, T active, T empty, BooleanSupplier valid, Object face) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "MAE2 capability " + type.getSimpleName();
                    default -> throw new UnsupportedOperationException(method.getName());
                };
            }
            try {
                boolean direction = face == null || args == null || args.length == 0
                    || !(method.getName().equals("inputsEnergy") || method.getName().equals("outputsEnergy")
                        || method.getName().equals("acceptEnergyFromNetwork")) || args[0] == face;
                return method.invoke(valid.getAsBoolean() && direction ? active : empty, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }));
    }
}
