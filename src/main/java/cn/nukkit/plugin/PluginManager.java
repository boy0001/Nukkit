package cn.nukkit.plugin;

import cn.nukkit.Server;
import cn.nukkit.command.PluginCommand;
import cn.nukkit.command.SimpleCommandMap;
import cn.nukkit.event.*;
import cn.nukkit.permission.Permissible;
import cn.nukkit.permission.Permission;
import cn.nukkit.timings.Timing;
import cn.nukkit.timings.Timings;
import cn.nukkit.utils.MainLogger;
import cn.nukkit.utils.PluginException;
import cn.nukkit.utils.Utils;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * @author MagicDroidX
 */
public class PluginManager {

    private final Server server;

    private final SimpleCommandMap commandMap;

    protected final Map<String, Plugin> plugins = new LinkedHashMap<>();

    protected final Map<String, Permission> permissions = new ConcurrentHashMap<>(8, 0.9f, 1);

    protected final Map<String, Permission> defaultPerms = new ConcurrentHashMap<>(8, 0.9f, 1);

    protected final Map<String, Permission> defaultPermsOp = new ConcurrentHashMap<>(8, 0.9f, 1);

    protected final Map<String, WeakHashMap<Permissible, Permissible>> permSubs = new ConcurrentHashMap<>(8, 0.9f, 1);

    protected final Map<Permissible, Permissible> defSubs = new WeakHashMap<>();

    protected final Map<Permissible, Permissible> defSubsOp = new WeakHashMap<>();

    protected final Map<String, PluginLoader> fileAssociations = new ConcurrentHashMap<>(8, 0.9f, 1);

    public PluginManager(Server server, SimpleCommandMap commandMap) {
        this.server = server;
        this.commandMap = commandMap;
    }

    public Plugin getPlugin(String name) {
        if (this.plugins.containsKey(name)) {
            return this.plugins.get(name);
        }
        return null;
    }

    public boolean registerInterface(Class<? extends PluginLoader> loaderClass) {
        if (loaderClass != null) {
            try {
                Constructor constructor = loaderClass.getDeclaredConstructor(Server.class);
                constructor.setAccessible(true);
                this.fileAssociations.put(loaderClass.getName(), (PluginLoader) constructor.newInstance(this.server));
                return true;
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    public Map<String, Plugin> getPlugins() {
        return plugins;
    }

    public Plugin loadPlugin(String path) {
        return this.loadPlugin(path, null);
    }

    public Plugin loadPlugin(File file) {
        return this.loadPlugin(file, null);
    }

    public Plugin loadPlugin(String path, Map<String, PluginLoader> loaders) {
        return this.loadPlugin(new File(path), loaders);
    }

    public Plugin loadPlugin(File file, Map<String, PluginLoader> loaders) {
        for (PluginLoader loader : (loaders == null ? this.fileAssociations : loaders).values()) {
            for (Pattern pattern : loader.getPluginFilters()) {
                if (pattern.matcher(file.getName()).matches()) {
                    PluginDescription description = loader.getPluginDescription(file);
                    if (description != null) {
                        try {
                            Plugin plugin = loader.loadPlugin(file);
                            if (plugin != null) {
                                this.plugins.put(plugin.getDescription().getName(), plugin);

                                List<PluginCommand> pluginCommands = this.parseYamlCommands(plugin);

                                if (!pluginCommands.isEmpty()) {
                                    this.commandMap.registerAll(plugin.getDescription().getName(), pluginCommands);
                                }

                                return plugin;
                            }
                        } catch (Exception e) {
                            Server.getInstance().getLogger().debug("Could not load plugin", e);
                            return null;
                        }
                    }
                }
            }
        }

        return null;
    }

    public Map<String, Plugin> loadPlugins(String dictionary) {
        return this.loadPlugins(new File(dictionary));
    }

    public Map<String, Plugin> loadPlugins(File dictionary) {
        return this.loadPlugins(dictionary, null);
    }

    public Map<String, Plugin> loadPlugins(String dictionary, List<String> newLoaders) {
        return this.loadPlugins(new File(dictionary), newLoaders);
    }

    public Map<String, Plugin> loadPlugins(File dictionary, List<String> newLoaders) {
        return this.loadPlugins(dictionary, newLoaders, false);
    }

    public Map<String, Plugin> loadPlugins(File dictionary, List<String> newLoaders, boolean includeDir) {
        final ForkJoinPool pool = new ForkJoinPool();
        if (dictionary.isDirectory()) {
            final Map<String, File> plugins = new ConcurrentHashMap<>();
            final Map<String, Plugin> loadedPlugins = new ConcurrentHashMap<>();
            final Map<String, List<String>> dependencies = new ConcurrentHashMap<>();
            final Map<String, List<String>> softDependencies = new ConcurrentHashMap<>();
            final Map<String, PluginLoader> loaders;
            if (newLoaders != null) {
                loaders = new LinkedHashMap<>();
                for (String key : newLoaders) {
                    if (this.fileAssociations.containsKey(key)) {
                        loaders.put(key, this.fileAssociations.get(key));
                    }
                }
            } else {
                loaders = this.fileAssociations;
            }

            for (final PluginLoader loader : loaders.values()) {
                for (File file : dictionary.listFiles((dir, name) -> {
                    for (Pattern pattern : loader.getPluginFilters()) {
                        if (pattern.matcher(name).matches()) {
                            return true;
                        }
                    }
                    return false;
                })) {
                    if (file.isDirectory() && !includeDir) {
                        continue;
                    }
                    pool.submit((Runnable) () -> {
                        try {
                            PluginDescription description = loader.getPluginDescription(file);
                            if (description != null) {
                                String name = description.getName();
                                if (plugins.containsKey(name) || getPlugin(name) != null) {
                                    server.getLogger().error(server.getLanguage().translateString("nukkit.plugin.duplicateError", name));
                                    return;
                                }

                                boolean compatible = false;

                                for (String version : description.getCompatibleAPIs()) {

                                    //Check the format: majorVersion.minorVersion.patch
                                    if (!Pattern.matches("[0-9]\\.[0-9]\\.[0-9]", version)) {
                                        server.getLogger().error(server.getLanguage().translateString("nukkit.plugin.loadError", new String[]{name, "Wrong API format"}));
                                        continue;
                                    }

                                    String[] versionArray = version.split("\\.");
                                    String[] apiVersion = server.getApiVersion().split("\\.");

                                    //Completely different API version
                                    if (!Objects.equals(Integer.valueOf(versionArray[0]), Integer.valueOf(apiVersion[0]))) {
                                        continue;
                                    }

                                    //If the plugin requires new API features, being backwards compatible
                                    if (Integer.valueOf(versionArray[1]) > Integer.valueOf(apiVersion[1])) {
                                        continue;
                                    }

                                    compatible = true;
                                    break;
                                }

                                if (!compatible) {
                                    server.getLogger().error(server.getLanguage().translateString("nukkit.plugin.loadError", new String[]{name, "%nukkit.plugin.incompatibleAPI"}));
                                }

                                plugins.put(name, file);

                                softDependencies.put(name, description.getSoftDepend());

                                dependencies.put(name, description.getDepend());

                                for (String before : description.getLoadBefore()) {
                                    if (softDependencies.containsKey(before)) {
                                        softDependencies.get(before).add(name);
                                    } else {
                                        List<String> list = new ArrayList<>();
                                        list.add(name);
                                        softDependencies.put(before, list);
                                    }
                                }
                            }
                        } catch (Throwable e) {
                            server.getLogger().error(server.getLanguage().translateString("nukkit.plugin" +
                                    ".fileError", new String[]{file.getName(), dictionary.toString(), Utils
                                    .getExceptionMessage(e)}));
                            MainLogger logger = server.getLogger();
                            if (logger != null) {
                                logger.logException(e instanceof Exception ? (Exception) e : new RuntimeException(e));
                            }
                        }
                    });
                }
            }
            // Wait for all the plugin descriptions to be read
            pool.awaitQuiescence(Long.MAX_VALUE, TimeUnit.MILLISECONDS);

            // While there are plugins left to load, we will load anything that has all of its dependencies loaded
            while (!plugins.isEmpty()) {
                final AtomicBoolean missingDependency = new AtomicBoolean(true);
                for (final String name : new ArrayList<>(plugins.keySet())) {
                    pool.submit((Runnable) () -> {
                        File file = plugins.get(name);
                        if (dependencies.containsKey(name)) {
                            for (String dependency : new ArrayList<>(dependencies.get(name))) {
                                if (loadedPlugins.containsKey(dependency) || getPlugin(dependency) != null) {
                                    dependencies.get(name).remove(dependency);
                                } else if (!plugins.containsKey(dependency)) {
                                    server.getLogger().critical(server.getLanguage().translateString("nukkit" +
                                            ".plugin.loadError", new String[]{name, "%nukkit.plugin.unknownDependency"}));
                                    break;
                                }
                            }

                            if (dependencies.get(name).isEmpty()) {
                                dependencies.remove(name);
                            }
                        }

                        if (softDependencies.containsKey(name)) {
                            for (String dependency : new ArrayList<>(softDependencies.get(name))) {
                                if (loadedPlugins.containsKey(dependency) || getPlugin(dependency) != null) {
                                    softDependencies.get(name).remove(dependency);
                                }
                            }

                            if (softDependencies.get(name).isEmpty()) {
                                softDependencies.remove(name);
                            }
                        }

                        if (!dependencies.containsKey(name) && !softDependencies.containsKey(name)) {
                            Plugin plugin = loadPlugin(file, loaders);
                            if (plugin != null) {
                                loadedPlugins.put(name, plugin);
                            } else {
                                server.getLogger().critical(server.getLanguage().translateString("nukkit.plugin.genericLoadError", name));
                            }
                            plugins.remove(name);
                            missingDependency.set(false);
                        }
                    });
                }
                // Wait for this round of plugin loads to finish
                pool.awaitQuiescence(Long.MAX_VALUE, TimeUnit.MILLISECONDS);

                // Attempt to load plugins with missing dependencies
                if (missingDependency.get()) {
                    for (String name : new ArrayList<>(plugins.keySet())) {
                        File file = plugins.get(name);
                        if (!dependencies.containsKey(name)) {
                            softDependencies.remove(name);
                            plugins.remove(name);
                            missingDependency.set(false);
                            Plugin plugin = this.loadPlugin(file, loaders);
                            if (plugin != null) {
                                loadedPlugins.put(name, plugin);
                            } else {
                                this.server.getLogger().critical(this.server.getLanguage().translateString("nukkit.plugin.genericLoadError", name));
                            }
                        }
                    }

                    if (missingDependency.get()) {
                        for (String name : plugins.keySet()) {
                            this.server.getLogger().critical(this.server.getLanguage().translateString("nukkit.plugin.loadError", new String[]{name, "%nukkit.plugin.circularDependency"}));
                        }
                        plugins.clear();
                    }
                }
            }

            // Finished
            pool.awaitQuiescence(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
            pool.shutdownNow();
            return loadedPlugins;
        } else {
            return new ConcurrentHashMap<>(8, 0.9f, 1);
        }
    }

    public Permission getPermission(String name) {
        if (this.permissions.containsKey(name)) {
            return this.permissions.get(name);
        }
        return null;
    }

    public boolean addPermission(Permission permission) {
        if (!this.permissions.containsKey(permission.getName())) {
            this.permissions.put(permission.getName(), permission);
            this.calculatePermissionDefault(permission);

            return true;
        }

        return false;
    }

    public void removePermission(String name) {
        this.permissions.remove(name);
    }

    public void removePermission(Permission permission) {
        this.removePermission(permission.getName());
    }

    public Map<String, Permission> getDefaultPermissions(boolean op) {
        if (op) {
            return this.defaultPermsOp;
        } else {
            return this.defaultPerms;
        }
    }

    public void recalculatePermissionDefaults(Permission permission) {
        if (this.permissions.containsKey(permission.getName())) {
            this.defaultPermsOp.remove(permission.getName());
            this.defaultPerms.remove(permission.getName());
            this.calculatePermissionDefault(permission);
        }
    }

    private void calculatePermissionDefault(Permission permission) {
        Timings.permissionDefaultTimer.startTiming();
        if (permission.getDefault().equals(Permission.DEFAULT_OP) || permission.getDefault().equals(Permission.DEFAULT_TRUE)) {
            this.defaultPermsOp.put(permission.getName(), permission);
            this.dirtyPermissibles(true);
        }

        if (permission.getDefault().equals(Permission.DEFAULT_NOT_OP) || permission.getDefault().equals(Permission.DEFAULT_TRUE)) {
            this.defaultPerms.put(permission.getName(), permission);
            this.dirtyPermissibles(false);
        }
        Timings.permissionDefaultTimer.startTiming();
    }

    private void dirtyPermissibles(boolean op) {
        for (Permissible p : this.getDefaultPermSubscriptions(op)) {
            p.recalculatePermissions();
        }
    }

    public void subscribeToPermission(String permission, Permissible permissible) {
        if (!this.permSubs.containsKey(permission)) {
            this.permSubs.put(permission, new WeakHashMap<>());
        }
        this.permSubs.get(permission).put(permissible, permissible);
    }

    public void unsubscribeFromPermission(String permission, Permissible permissible) {
        if (this.permSubs.containsKey(permission)) {
            this.permSubs.get(permission).remove(permissible);
            if (this.permSubs.get(permission).size() == 0) {
                this.permSubs.remove(permission);
            }
        }
    }

    public Set<Permissible> getPermissionSubscriptions(String permission) {
        if (this.permSubs.containsKey(permission)) {
            Set<Permissible> subs = new HashSet<>();
            for (Permissible p : this.permSubs.get(permission).values()) {
                subs.add(p);
            }
            return subs;
        }

        return new HashSet<>();
    }

    public void subscribeToDefaultPerms(boolean op, Permissible permissible) {
        if (op) {
            this.defSubsOp.put(permissible, permissible);
        } else {
            this.defSubs.put(permissible, permissible);
        }
    }

    public void unsubscribeFromDefaultPerms(boolean op, Permissible permissible) {
        if (op) {
            this.defSubsOp.remove(permissible);
        } else {
            this.defSubs.remove(permissible);
        }
    }

    public Set<Permissible> getDefaultPermSubscriptions(boolean op) {
        Set<Permissible> subs = new HashSet<>();
        if (op) {
            for (Permissible p : this.defSubsOp.values()) {
                subs.add(p);
            }
        } else {
            for (Permissible p : this.defSubs.values()) {
                subs.add(p);
            }
        }
        return subs;
    }

    public Map<String, Permission> getPermissions() {
        return permissions;
    }

    public boolean isPluginEnabled(Plugin plugin) {
        if (plugin != null && this.plugins.containsKey(plugin.getDescription().getName())) {
            return plugin.isEnabled();
        } else {
            return false;
        }
    }

    public void enablePlugin(Plugin plugin) {
        if (!plugin.isEnabled()) {
            try {
                for (Permission permission : plugin.getDescription().getPermissions()) {
                    this.addPermission(permission);
                }
                plugin.getPluginLoader().enablePlugin(plugin);
            } catch (Throwable e) {
                MainLogger logger = this.server.getLogger();
                if (logger != null) {
                    logger.logException(new RuntimeException(e));
                }
                this.disablePlugin(plugin);
            }
        }
    }

    protected List<PluginCommand> parseYamlCommands(Plugin plugin) {
        List<PluginCommand> pluginCmds = new ArrayList<>();

        for (Map.Entry entry : plugin.getDescription().getCommands().entrySet()) {
            String key = (String) entry.getKey();
            Object data = entry.getValue();
            if (key.contains(":")) {
                this.server.getLogger().critical(this.server.getLanguage().translateString("nukkit.plugin.commandError", new String[]{key, plugin.getDescription().getFullName()}));
                continue;
            }
            if (data instanceof Map) {
                PluginCommand newCmd = new PluginCommand<>(key, plugin);

                if (((Map) data).containsKey("description")) {
                    newCmd.setDescription((String) ((Map) data).get("description"));
                }

                if (((Map) data).containsKey("usage")) {
                    newCmd.setUsage((String) ((Map) data).get("usage"));
                }

                if (((Map) data).containsKey("aliases")) {
                    Object aliases = ((Map) data).get("aliases");
                    if (aliases instanceof List) {
                        List<String> aliasList = new ArrayList<>();
                        for (String alias : (List<String>) aliases) {
                            if (alias.contains(":")) {
                                this.server.getLogger().critical(this.server.getLanguage().translateString("nukkit.plugin.aliasError", new String[]{alias, plugin.getDescription().getFullName()}));
                                continue;
                            }
                            aliasList.add(alias);
                        }

                        newCmd.setAliases(aliasList.stream().toArray(String[]::new));
                    }
                }

                if (((Map) data).containsKey("permission")) {
                    newCmd.setPermission((String) ((Map) data).get("permission"));
                }

                if (((Map) data).containsKey("permission-message")) {
                    newCmd.setPermissionMessage((String) ((Map) data).get("permission-message"));
                }

                pluginCmds.add(newCmd);
            }
        }

        return pluginCmds;
    }

    public void disablePlugins() {
        ListIterator<Plugin> plugins = new ArrayList<>(this.getPlugins().values()).listIterator(this.getPlugins().size());

        while (plugins.hasPrevious()) {
            this.disablePlugin(plugins.previous());
        }
    }

    public void disablePlugin(Plugin plugin) {
        if (plugin.isEnabled()) {
            try {
                plugin.getPluginLoader().disablePlugin(plugin);
            } catch (Exception e) {
                MainLogger logger = this.server.getLogger();
                if (logger != null) {
                    logger.logException(e);
                }
            }

            this.server.getScheduler().cancelTask(plugin);
            HandlerList.unregisterAll(plugin);
            for (Permission permission : plugin.getDescription().getPermissions()) {
                this.removePermission(permission);
            }
        }
    }

    public void clearPlugins() {
        this.disablePlugins();
        this.plugins.clear();
        this.fileAssociations.clear();
        this.permissions.clear();
        this.defaultPerms.clear();
        this.defaultPermsOp.clear();
    }

    public void callEvent(Event event) {
        try {
            HandlerList handler = event.getHandlerList();
            RegisteredListener[] listeners = handler.getRegisteredListeners();
            for (RegisteredListener registration : listeners) {
                if (!registration.getPlugin().isEnabled()) {
                    continue;
                }
                try {
                    registration.callEvent(event);
                } catch (Exception e) {
                    this.server.getLogger().critical(this.server.getLanguage().translateString("nukkit.plugin.eventError", new String[]{event.getEventName(), registration.getPlugin().getDescription().getFullName(), e.getMessage(), registration.getListener().getClass().getName()}));
                    this.server.getLogger().logException(e);
                }
            }
        } catch (IllegalAccessException e) {
            this.server.getLogger().logException(e);
        }
    }

    public void registerEvents(Listener listener, Plugin plugin) {
        if (!plugin.isEnabled()) {
            throw new PluginException("Plugin attempted to register " + listener.getClass().getName() + " while not enabled");
        }

        Map<Class<? extends Event>, Set<RegisteredListener>> ret = new ConcurrentHashMap<>(8, 0.9f, 1);
        Set<Method> methods;
        try {
            Method[] publicMethods = listener.getClass().getMethods();
            Method[] privateMethods = listener.getClass().getDeclaredMethods();
            methods = new HashSet<>(publicMethods.length + privateMethods.length, 1.0f);
            Collections.addAll(methods, publicMethods);
            Collections.addAll(methods, privateMethods);
        } catch (NoClassDefFoundError e) {
            plugin.getLogger().error("Plugin " + plugin.getDescription().getFullName() + " has failed to register events for " + listener.getClass() + " because " + e.getMessage() + " does not exist.");
            return;
        }

        for (final Method method : methods) {
            final EventHandler eh = method.getAnnotation(EventHandler.class);
            if (eh == null) continue;
            if (method.isBridge() || method.isSynthetic()) {
                continue;
            }
            final Class<?> checkClass;

            if (method.getParameterTypes().length != 1 || !Event.class.isAssignableFrom(checkClass = method.getParameterTypes()[0])) {
                plugin.getLogger().error(plugin.getDescription().getFullName() + " attempted to register an invalid EventHandler method signature \"" + method.toGenericString() + "\" in " + listener.getClass());
                continue;
            }

            final Class<? extends Event> eventClass = checkClass.asSubclass(Event.class);
            method.setAccessible(true);
            Set<RegisteredListener> eventSet = ret.get(eventClass);
            if (eventSet == null) {
                eventSet = new HashSet<>();
                ret.put(eventClass, eventSet);
            }

            for (Class<?> clazz = eventClass; Event.class.isAssignableFrom(clazz); clazz = clazz.getSuperclass()) {
                // This loop checks for extending deprecated events
                if (clazz.getAnnotation(Deprecated.class) != null) {
                    if (Boolean.valueOf(String.valueOf(this.server.getConfig("settings.deprecated-verbpse", true)))) {
                        this.server.getLogger().warning(this.server.getLanguage().translateString("nukkit.plugin.deprecatedEvent", new String[]{plugin.getName(), clazz.getName(), listener.getClass().getName() + "." + method.getName() + "()"}));
                    }
                    break;
                }
            }
            this.registerEvent(eventClass, listener, eh.priority(), new MethodEventExecutor(method), plugin, eh.ignoreCancelled());
        }
    }

    public void registerEvent(Class<? extends Event> event, Listener listener, EventPriority priority, EventExecutor executor, Plugin plugin) throws PluginException {
        this.registerEvent(event, listener, priority, executor, plugin, false);
    }

    public void registerEvent(Class<? extends Event> event, Listener listener, EventPriority priority, EventExecutor executor, Plugin plugin, boolean ignoreCancelled) throws PluginException {
        if (!plugin.isEnabled()) {
            throw new PluginException("Plugin attempted to register " + event + " while not enabled");
        }

        try {
            Timing timing = Timings.getPluginEventTiming(event, listener, executor, plugin);
            this.getEventListeners(event).register(new RegisteredListener(listener, executor, priority, plugin, ignoreCancelled, timing));
        } catch (IllegalAccessException e) {
            Server.getInstance().getLogger().logException(e);
        }
    }

    private HandlerList getEventListeners(Class<? extends Event> type) throws IllegalAccessException {
        try {
            Method method = getRegistrationClass(type).getDeclaredMethod("getHandlers");
            method.setAccessible(true);
            return (HandlerList) method.invoke(null);
        } catch (Exception e) {
            throw new IllegalAccessException(Utils.getExceptionMessage(e));
        }
    }

    private Class<? extends Event> getRegistrationClass(Class<? extends Event> clazz) throws IllegalAccessException {
        try {
            clazz.getDeclaredMethod("getHandlers");
            return clazz;
        } catch (NoSuchMethodException e) {
            if (clazz.getSuperclass() != null
                    && !clazz.getSuperclass().equals(Event.class)
                    && Event.class.isAssignableFrom(clazz.getSuperclass())) {
                return getRegistrationClass(clazz.getSuperclass().asSubclass(Event.class));
            } else {
                throw new IllegalAccessException("Unable to find handler list for event " + clazz.getName() + ". Static getHandlers method required!");
            }
        }
    }
}
