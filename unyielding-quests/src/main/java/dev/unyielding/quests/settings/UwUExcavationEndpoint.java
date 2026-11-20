package dev.unyielding.quests.settings;

import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/** Optional integration with the public v1 control in UwU Dimension. */
final class UwUExcavationEndpoint implements ModSettings.ExcavationEndpoint {
    private record Api(Method status, Method set, Method enabled, Method source) {}
    private record Reading(boolean enabled, boolean worldOverride) {}
    private static final Optional<Api> API = discover();
    private final Api api;
    private final CommandSourceStack actor;

    private UwUExcavationEndpoint(Api api, CommandSourceStack actor) {
        this.api = api;
        this.actor = actor;
    }

    private static Optional<Api> discover() {
        try {
            String name = "dev.uwu.dimension.LauncherExcavationControl";
            var loader = UwUExcavationEndpoint.class.getClassLoader();
            Class<?> control = Class.forName(name, false, loader);
            if (control.getField("API_VERSION").getInt(null) != 1) return Optional.empty();
            Class<?> status = Class.forName(name + "$Status", false, loader);
            return Optional.of(new Api(control.getMethod("status", CommandSourceStack.class),
                    control.getMethod("set", CommandSourceStack.class, boolean.class),
                    status.getMethod("enabled"), status.getMethod("source")));
        } catch (ReflectiveOperationException | LinkageError e) {
            return Optional.empty();
        }
    }

    static Optional<ModSettings.ExcavationEndpoint> bind(ServerPlayer player) {
        return API.map(api -> new UwUExcavationEndpoint(api, player.createCommandSourceStack()));
    }

    private Reading call(Method method, Object... args) {
        try {
            Object result = method.invoke(null, args);
            if (result instanceof Optional<?> optional && optional.isPresent()) {
                Object status = optional.get();
                String source = ((Enum<?>)api.source.invoke(status)).name();
                if (source.equals("CONFIGURATION") || source.equals("WORLD_OVERRIDE")) {
                    return new Reading((boolean)api.enabled.invoke(status), source.equals("WORLD_OVERRIDE"));
                }
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IllegalStateException("UwU Dimension could not confirm the excavation setting.", e);
        }
        throw new IllegalStateException("UwU Dimension could not read the excavation setting for this world.");
    }

    public ModSettings.State read() {
        return new ModSettings.State(call(api.status, actor).enabled, ModSettings.Scope.WORLD, ModSettings.Applies.LIVE);
    }

    public boolean compareAndSet(boolean expected, boolean enabled) {
        if (call(api.status, actor).enabled != expected) return false;
        Reading saved = call(api.set, actor, enabled);
        if (!saved.worldOverride || saved.enabled != enabled || !saved.equals(call(api.status, actor))) {
            throw new IllegalStateException("UwU Dimension could not confirm the saved world override.");
        }
        return true;
    }
}
