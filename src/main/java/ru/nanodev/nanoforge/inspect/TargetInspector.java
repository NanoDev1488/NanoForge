package ru.nanodev.nanoforge.inspect;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

/**
 * Достаёт из jar-файла целевого плагина список всех его классов
 * и для каждого класса - его методы/поля/конструкторы (через рефлексию),
 * чтобы при написании action'ов с type: call сразу было видно,
 * что вообще доступно у плагина, к которому делается аддон.
 */
public class TargetInspector {

    /**
     * @return true если дамп успешно записан
     */
    public static boolean dump(Plugin targetPlugin, File outputFile) {
        try {
            File jarFile = locateJar(targetPlugin);
            if (jarFile == null || !jarFile.exists()) {
                writeError(outputFile, targetPlugin, "Не удалось определить jar-файл плагина.");
                return false;
            }

            List<String> allClassNames = listClassNames(jarFile);
            String packagePrefix = pluginPackagePrefix(targetPlugin);
            List<String> classNames = packagePrefix.isEmpty()
                    ? allClassNames
                    : allClassNames.stream().filter(n -> n.startsWith(packagePrefix)).collect(Collectors.toList());
            ClassLoader loader = targetPlugin.getClass().getClassLoader();

            try (PrintWriter out = new PrintWriter(outputFile, "UTF-8")) {
                out.println("=== Дамп API плагина: " + targetPlugin.getName() + " ===");
                out.println("Версия: " + targetPlugin.getDescription().getVersion());
                out.println("Главный класс: " + targetPlugin.getClass().getName());
                out.println("Jar: " + jarFile.getAbsolutePath());
                out.println("Всего классов в jar: " + allClassNames.size()
                        + (classNames.size() != allClassNames.size()
                        ? " (показаны только из пакета '" + packagePrefix + "': " + classNames.size() + ")"
                        : ""));
                out.println();
                out.println("Файл сгенерирован автоматически при создании аддона.");
                out.println("Используй имена классов/методов ниже для action'ов type: call");
                out.println("(actions: - type: call, plugin: " + targetPlugin.getName() + ", method: <имя>, args: [...])");
                out.println();

                int dumped = 0;
                int skipped = 0;
                for (String className : classNames) {
                    Class<?> clazz;
                    try {
                        clazz = Class.forName(className, false, loader);
                    } catch (Throwable t) {
                        skipped++;
                        continue;
                    }
                    writeClass(out, clazz);
                    dumped++;
                }

                out.println();
                out.println("--- Итого: описано классов " + dumped + ", пропущено (не удалось загрузить) " + skipped + " ---");
            }
            return true;
        } catch (Exception e) {
            writeError(outputFile, targetPlugin, "Ошибка дампа: " + e);
            return false;
        }
    }

    /**
     * Первые два сегмента пакета главного класса плагина (например "com.example" для
     * "com.example.myplugin.MyPlugin") - используется как фильтр, чтобы не дампить
     * шейднутые в jar сторонние библиотеки (org.bukkit, com.google, org.yaml и т.д.),
     * оставляя только код самого плагина.
     */
    private static String pluginPackagePrefix(Plugin plugin) {
        Package pkg = plugin.getClass().getPackage();
        if (pkg == null) return "";
        String[] parts = pkg.getName().split("\\.");
        if (parts.length >= 2) return parts[0] + "." + parts[1];
        return pkg.getName();
    }

    private static void writeClass(PrintWriter out, Class<?> clazz) {
        out.println("--- Класс: " + clazz.getName() + " ---");

        if (clazz.getSuperclass() != null && clazz.getSuperclass() != Object.class) {
            out.println("extends " + clazz.getSuperclass().getName());
        }
        if (clazz.getInterfaces().length > 0) {
            String interfaces = java.util.Arrays.stream(clazz.getInterfaces())
                    .map(Class::getName).collect(Collectors.joining(", "));
            out.println("implements " + interfaces);
        }

        // конструкторы
        Constructor<?>[] ctors = safeGetDeclaredConstructors(clazz);
        if (ctors.length > 0) {
            out.println("Конструкторы:");
            for (Constructor<?> c : ctors) {
                if (!isVisible(c.getModifiers())) continue;
                out.println("  " + Modifier.toString(c.getModifiers() & (Modifier.PUBLIC | Modifier.PROTECTED))
                        + " " + clazz.getSimpleName() + "(" + paramTypes(c.getParameterTypes()) + ")");
            }
        }

        // методы (только объявленные в этом классе, public/protected, без синтетики)
        Method[] methods = safeGetDeclaredMethods(clazz);
        List<Method> visible = new ArrayList<>();
        for (Method m : methods) {
            if (!isVisible(m.getModifiers())) continue;
            if (m.isSynthetic() || m.isBridge()) continue;
            visible.add(m);
        }
        if (!visible.isEmpty()) {
            out.println("Методы:");
            for (Method m : visible) {
                String mods = Modifier.toString(m.getModifiers()
                        & (Modifier.PUBLIC | Modifier.PROTECTED | Modifier.STATIC));
                out.println("  " + mods + " " + m.getReturnType().getSimpleName() + " "
                        + m.getName() + "(" + paramTypes(m.getParameterTypes()) + ")");
            }
        }

        // поля
        Field[] fields = safeGetDeclaredFields(clazz);
        List<Field> visibleFields = new ArrayList<>();
        for (Field f : fields) {
            if (!isVisible(f.getModifiers())) continue;
            if (f.isSynthetic()) continue;
            visibleFields.add(f);
        }
        if (!visibleFields.isEmpty()) {
            out.println("Поля:");
            for (Field f : visibleFields) {
                String mods = Modifier.toString(f.getModifiers()
                        & (Modifier.PUBLIC | Modifier.PROTECTED | Modifier.STATIC | Modifier.FINAL));
                out.println("  " + mods + " " + f.getType().getSimpleName() + " " + f.getName());
            }
        }

        out.println();
    }

    private static boolean isVisible(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    private static String paramTypes(Class<?>[] types) {
        return java.util.Arrays.stream(types).map(Class::getSimpleName).collect(Collectors.joining(", "));
    }

    private static Method[] safeGetDeclaredMethods(Class<?> c) {
        try {
            return c.getDeclaredMethods();
        } catch (Throwable t) {
            return new Method[0];
        }
    }

    private static Field[] safeGetDeclaredFields(Class<?> c) {
        try {
            return c.getDeclaredFields();
        } catch (Throwable t) {
            return new Field[0];
        }
    }

    private static Constructor<?>[] safeGetDeclaredConstructors(Class<?> c) {
        try {
            return c.getDeclaredConstructors();
        } catch (Throwable t) {
            return new Constructor<?>[0];
        }
    }

    private static File locateJar(Plugin plugin) {
        try {
            URL location = plugin.getClass().getProtectionDomain().getCodeSource().getLocation();
            return new File(location.toURI());
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> listClassNames(File jarFile) throws Exception {
        List<String> names = new ArrayList<>();
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String n = entry.getName();
                if (!n.endsWith(".class") || n.contains("module-info")) continue;
                String className = n.substring(0, n.length() - 6).replace('/', '.');
                names.add(className);
            }
        }
        return names;
    }

    private static void writeError(File outputFile, Plugin plugin, String message) {
        try (PrintWriter out = new PrintWriter(outputFile, "UTF-8")) {
            out.println("=== Дамп API плагина: " + (plugin != null ? plugin.getName() : "?") + " ===");
            out.println(message);
        } catch (Exception ignored) {
        }
    }
}

// by t.me/NanoDev_mc
