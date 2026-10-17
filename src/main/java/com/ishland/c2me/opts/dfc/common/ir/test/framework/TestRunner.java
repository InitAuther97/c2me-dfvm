package com.ishland.c2me.opts.dfc.common.ir.test.framework;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/// Minimal reflection-based test runner: every public no-arg void method whose
/// name starts with "test" on a registered class is treated as a test case.
public final class TestRunner {

    private final List<Class<?>> testClasses = new ArrayList<>();

    public TestRunner register(Class<?> testClass) {
        testClasses.add(testClass);
        return this;
    }

    /// @return number of failed tests
    public int run() {
        int passed = 0, failed = 0;
        for (Class<?> testClass : testClasses) {
            final List<Method> methods = new ArrayList<>();
            for (Method method : testClass.getDeclaredMethods()) {
                if (method.getName().startsWith("test")
                        && method.getParameterCount() == 0
                        && method.getReturnType() == void.class
                        && Modifier.isPublic(method.getModifiers())) {
                    methods.add(method);
                }
            }
            methods.sort(Comparator.comparing(Method::getName));
            if (methods.isEmpty()) {
                System.out.println("[SKIP] " + testClass.getSimpleName() + " (no test methods)");
                continue;
            }
            final Object instance;
            try {
                instance = testClass.getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                System.out.println("[FAIL] " + testClass.getSimpleName() + " (cannot instantiate: " + e + ")");
                failed++;
                continue;
            }
            for (Method method : methods) {
                final String name = testClass.getSimpleName() + "." + method.getName();
                try {
                    method.invoke(instance);
                    passed++;
                    System.out.println("[PASS] " + name);
                } catch (InvocationTargetException e) {
                    failed++;
                    final Throwable cause = e.getCause();
                    System.out.println("[FAIL] " + name + ": " + cause);
                    for (StackTraceElement element : cause.getStackTrace()) {
                        if (element.getClassName().startsWith("com.ishland")) {
                            System.out.println("         at " + element);
                        }
                    }
                } catch (IllegalAccessException e) {
                    failed++;
                    System.out.println("[FAIL] " + name + ": " + e);
                }
            }
        }
        System.out.println();
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        return failed;
    }
}