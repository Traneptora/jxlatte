package com.thebombzen.jxlatte.io;

import java.io.PrintWriter;
import java.util.Arrays;

import com.thebombzen.jxlatte.JXLOptions;
import com.thebombzen.jxlatte.util.functional.FunctionalHelper;

public class Loggers {
    public final JXLOptions options;
    public final PrintWriter err;

    public Loggers(JXLOptions options, PrintWriter err) {
        this.options = options;
        this.err = err;
    }

    public void log(int logLevel, String format, Object... args) {
        if (logLevel > options.verbosity)
            return;
        Object[] things = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null) {
                things[i] = "null";
            } else if (args[i].getClass().isArray()) {
                Class<?> argClass = args[i].getClass();
                Class<?> componentType = argClass.getComponentType();
                if (componentType.isArray()) {
                    Object[] deep = (Object[])args[i];
                    things[i] = Arrays.deepToString(deep);
                } else {
                    try {
                        things[i] = Arrays.class.getMethod("toString",
                            componentType.isPrimitive() ? argClass : Object[].class)
                            .invoke(null, args[i]);
                    } catch (Exception ex) {
                        FunctionalHelper.sneakyThrow(ex);
                    }
                }
            } else {
                things[i] = args[i];
            }
        }
        err.println(String.format(format, things));
        err.flush();
    }

    public void log(int logLevel, Object arg) {
        log(logLevel, "%s", arg);
    }
}
