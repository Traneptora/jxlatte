package com.traneptora.jxlatte.io;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.stream.Stream;

import com.traneptora.jxlatte.JXLOptions;
import com.traneptora.jxlatte.util.functional.FunctionalHelper;

public class Loggers {

    public static final int LOG_BASE = 0;
    public static final int LOG_INFO = 8;
    public static final int LOG_VERBOSE = 16;
    public static final int LOG_TRACE = 24;

    public final JXLOptions options;
    public final PrintWriter err;

    public Loggers(JXLOptions options, PrintWriter err) {
        this.options = options;
        this.err = err;
    }

    private static Object deepToThing(Object arg) {
        if (arg == null)
            return "null";
        if (arg.getClass().isArray()) {
            Class<?> argClass = arg.getClass();
            Class<?> componentType = argClass.getComponentType();
            if (componentType.isArray()) {
                Object[] deep = (Object[])arg;
                return Arrays.deepToString(deep);
            } else {
                try {
                    return Arrays.class.getMethod("toString", componentType.isPrimitive() ?
                        argClass : Object[].class).invoke(null, arg);
                } catch (Exception ex) {
                    FunctionalHelper.sneakyThrow(ex);
                }
            }
        }
        return arg;
    }

    public static String deepToString(Object arg) {
        return deepToThing(arg).toString();
    }

    public void log(int logLevel, String format, Object... args) {
        if (logLevel > options.verbosity)
            return;
        Object[] things = Stream.of(args).map(Loggers::deepToThing).toArray();
        err.println(String.format(format, things));
        err.flush();
    }

    public void log(int logLevel, Object arg) {
        log(logLevel, "%s", arg);
    }
}
