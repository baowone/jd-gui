/*
 * Copyright (c) 2008-2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.gui;

import org.jd.gui.util.exception.ExceptionUtil;

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

public class OsxApp extends App {

    public static void main(String[] args) {
        // Create the main UI first (sets the shared 'controller')
        App.main(args);

        // Wire up macOS integration (dock icon file-open, quit, about).
        // Java 9+ removed com.apple.eawt; use java.awt.Desktop there.
        // On Java 8 fall back to com.apple.eawt (isolated in its own method so the
        // class is never loaded/verified on Java 9+, avoiding NoClassDefFoundError).
        try {
            if (isJava9OrLater()) {
                setupDesktopHandlers();
            } else {
                setupEawtHandlers();
            }
        } catch (Throwable t) {
            assert ExceptionUtil.printStackTrace(t);
        }
    }

    private static boolean isJava9OrLater() {
        String v = System.getProperty("java.specification.version", "1.8");
        if (v.startsWith("1.")) {
            return false;
        }
        try {
            return Integer.parseInt(v) >= 9;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    // Java 9+ path: reflectively use java.awt.Desktop so this still compiles on JDK 8.
    private static void setupDesktopHandlers() throws Exception {
        Class<?> desktopClass = Class.forName("java.awt.Desktop");
        Object desktop = desktopClass.getMethod("getDesktop").invoke(null);

        Class<?> openFilesHandlerClass = Class.forName("java.awt.desktop.OpenFilesHandler");
        Class<?> quitHandlerClass = Class.forName("java.awt.desktop.QuitHandler");

        Object openFilesHandler = Proxy.newProxyInstance(
                OsxApp.class.getClassLoader(),
                new Class<?>[] { openFilesHandlerClass },
                new OpenFilesInvocationHandler());
        desktopClass.getMethod("setOpenFileHandler", openFilesHandlerClass).invoke(desktop, openFilesHandler);

        Object quitHandler = Proxy.newProxyInstance(
                OsxApp.class.getClassLoader(),
                new Class<?>[] { quitHandlerClass },
                (proxy, method, args) -> {
                    if ("handleQuitRequestWith".equals(method.getName())) {
                        System.exit(0);
                    }
                    return null;
                });
        desktopClass.getMethod("setQuitHandler", quitHandlerClass).invoke(desktop, quitHandler);
    }

    // Java 8 path. Kept in its own method so the JVM never loads
    // com.apple.eawt.* when running on Java 9+ (where it no longer exists).
    @SuppressWarnings("deprecation")
    private static void setupEawtHandlers() {
        com.apple.eawt.Application application = com.apple.eawt.Application.getApplication();
        application.setOpenFileHandler(e -> controller.openFiles(e.getFiles()));
        application.setQuitHandler((e, r) -> System.exit(0));
    }

    // Routes OpenFilesEvent.getFiles() to the controller via reflection.
    private static final class OpenFilesInvocationHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("openFiles".equals(method.getName()) && args != null && args.length > 0) {
                Object event = args[0];
                Method getFiles = event.getClass().getMethod("getFiles");
                @SuppressWarnings("unchecked")
                List<File> files = (List<File>) getFiles.invoke(event);
                controller.openFiles(files);
            }
            return null;
        }
    }
}
