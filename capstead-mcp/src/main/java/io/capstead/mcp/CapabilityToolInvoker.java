package io.capstead.mcp;

import io.capstead.runtime.CapabilityDescriptor;

import org.springframework.util.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Dispatches an MCP {@code tools/call} onto the governed capability method.
 *
 * <p>Invocation goes through the capability bean's Spring proxy, so Capstead's governance —
 * {@code @DailyBudget} enforcement, execution recording, cost attribution — is applied exactly as
 * it would be for a normal call. Business arguments arrive as a JSON object (a {@link Map}); they
 * are bound positionally by parameter name and coerced to the method's parameter types.
 */
public class CapabilityToolInvoker {

    private final CapabilityToolCatalog catalog;

    public CapabilityToolInvoker(CapabilityToolCatalog catalog) {
        this.catalog = catalog;
    }

    /** Invokes the named tool with the given arguments, capturing failures as error results. */
    public CapabilityToolResult call(String toolName, Map<String, Object> arguments) {
        CapabilityDescriptor descriptor = catalog.descriptorFor(toolName).orElse(null);
        if (descriptor == null) {
            return CapabilityToolResult.error("Unknown capability tool: " + toolName);
        }

        Object[] resolved;
        try {
            resolved = bindArguments(descriptor, arguments == null ? Map.of() : arguments);
        } catch (RuntimeException ex) {
            return CapabilityToolResult.error("Invalid arguments for '" + toolName + "': " + messageOf(ex));
        }

        Method method = descriptor.method();
        ReflectionUtils.makeAccessible(method);
        try {
            Object result = method.invoke(descriptor.bean(), resolved);
            return CapabilityToolResult.ok(result == null ? "" : String.valueOf(result));
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getTargetException();
            return CapabilityToolResult.error(cause.getClass().getSimpleName() + ": " + messageOf(cause));
        } catch (IllegalAccessException ex) {
            return CapabilityToolResult.error("Capability tool '" + toolName + "' is not accessible");
        }
    }

    private Object[] bindArguments(CapabilityDescriptor descriptor, Map<String, Object> arguments) {
        String[] names = descriptor.parameterNames();
        Class<?>[] types = descriptor.method().getParameterTypes();
        Object[] resolved = new Object[names.length];
        for (int i = 0; i < names.length; i++) {
            resolved[i] = coerce(arguments.get(names[i]), types[i]);
        }
        return resolved;
    }

    private static Object coerce(Object value, Class<?> type) {
        if (value == null || type.isInstance(value)) {
            return value;
        }
        if (type == String.class) {
            return value.toString();
        }
        if (value instanceof Number number) {
            if (type == int.class || type == Integer.class) {
                return number.intValue();
            }
            if (type == long.class || type == Long.class) {
                return number.longValue();
            }
            if (type == double.class || type == Double.class) {
                return number.doubleValue();
            }
            if (type == float.class || type == Float.class) {
                return number.floatValue();
            }
            if (type == short.class || type == Short.class) {
                return number.shortValue();
            }
            if (type == byte.class || type == Byte.class) {
                return number.byteValue();
            }
        }
        String text = value.toString();
        if (type == int.class || type == Integer.class) {
            return Integer.valueOf(text);
        }
        if (type == long.class || type == Long.class) {
            return Long.valueOf(text);
        }
        if (type == double.class || type == Double.class) {
            return Double.valueOf(text);
        }
        if (type == float.class || type == Float.class) {
            return Float.valueOf(text);
        }
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.valueOf(text);
        }
        return value;
    }

    private static String messageOf(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }
}
