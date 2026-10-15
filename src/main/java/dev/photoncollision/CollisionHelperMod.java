package dev.photoncollision;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import org.slf4j.LoggerFactory;

@Mod(value = "photon_collision_helper", dist = Dist.CLIENT)
public final class CollisionHelperMod {
    public CollisionHelperMod() {
        NativeCollisionEditor.register();
        LoggerFactory.getLogger("Photon Collision Helper").info("Independent collision emitter extension loaded; Photon code is unchanged");
    }
}
