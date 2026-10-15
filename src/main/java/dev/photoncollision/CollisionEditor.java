package dev.photoncollision;

import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleConfig;

import java.lang.reflect.Field;
import java.util.List;

public final class CollisionEditor {
    private record Location(ConfiguratorGroup parent, ConfiguratorGroup group) {}

    private CollisionEditor() {}

    public static void attachToPhysics(ConfiguratorGroup root) {
        Location physics = find(root, ParticleConfig.class, "physics");
        Location extension = find(root, FastCollisionEmitter.class, "collision");
        if (physics == null || extension == null || extension.parent == physics.group) return;
        extension.parent.removeConfigurator(extension.group);
        physics.group.addConfigurator(extension.group);
    }

    private static Location find(ConfiguratorGroup root, Class<?> owner, String name) {
        for (var row : List.copyOf(root.getConfigurators())) {
            if (!(row instanceof ConfiguratorGroup group)) continue;
            Field field = group.getSourceField();
            if (field != null && field.getDeclaringClass() == owner && field.getName().equals(name)) return new Location(root, group);
            Location nested = find(group, owner, name);
            if (nested != null) return nested;
        }
        return null;
    }
}
