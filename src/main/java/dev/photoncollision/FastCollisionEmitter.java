package dev.photoncollision;

import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.lowdragmc.photon.client.fx.EntityEffectExecutor;
import com.lowdragmc.photon.client.fx.FXRuntime;
import com.lowdragmc.photon.client.fx.timeline.AnimatedPropertyType;
import com.lowdragmc.photon.client.gameobject.FXObject;
import com.lowdragmc.photon.client.gameobject.FXObjectType;
import com.lowdragmc.photon.client.gameobject.IFXObject;
import com.lowdragmc.photon.client.gameobject.RuntimeBinding;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleConfig;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.client.gameobject.particle.TileParticle;
import net.minecraft.world.phys.Vec3;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

public final class FastCollisionEmitter extends ParticleEmitter {
    @LDLRegisterClient(name = "collision_emitter", registry = "photon:fx_object")
    public static final FXObjectType TYPE = new FXObjectType() {
        @Override public IFXObject create() { return new FastCollisionEmitter(); }
        @Override public IGuiTexture icon() { return ParticleEmitter.TYPE.icon(); }
        @Override public int version() { return 1; }
        @Override public List<RuntimeBinding> runtimeBindings() { return ParticleEmitter.TYPE.runtimeBindings(); }
        @Override public List<AnimatedPropertyType> animatableProperties(FXObject target) { return ParticleEmitter.TYPE.animatableProperties(target); }
        @Override public RuntimeBinding resolveRuntimeBinding(FXObject target, String path) { return ParticleEmitter.TYPE.resolveRuntimeBinding(target, path); }
    };

    @Persisted @Configurable(name = "photon_collision_helper.editor.title", subConfigurable = true, tips = "photon_collision_helper.editor.title.tips")
    public CollisionSettings collision = new CollisionSettings();
    private final CollisionFrame ownFrame = new CollisionFrame();
    private FastCollisionEmitter cacheOwner;
    private CollisionFrame currentFrame;
    private IntOpenHashSet sourceEntities;
    private final ArrayDeque<Map<String, Object>> recentHits = new ArrayDeque<>();
    private long hitCount;
    private long simulationNanos;

    public FastCollisionEmitter() {
        super();
    }

    private FastCollisionEmitter(ParticleConfig config, CollisionSettings settings) {
        super(config);
        collision = settings;
    }

    @Override public FXObjectType getFXObjectType() { return TYPE; }
    @Override public void buildConfigurator(ConfiguratorGroup father) {
        super.buildConfigurator(father);
        CollisionEditor.attachToPhysics(father);
    }
    @Override public ParticleEmitter shallowCopy() { return new FastCollisionEmitter(config, collision); }
    @Override protected TileParticle createNewParticle() { return collision.backend == CollisionSettings.Backend.NATIVE ? super.createNewParticle() : new FastCollisionParticle(this, config); }

    @Override
    protected void onTickBegin() {
        super.onTickBegin();
        prepareFrame();
    }

    private void prepareFrame() {
        if (collision.backend == CollisionSettings.Backend.NATIVE || getLevel() == null) return;
        if (cacheOwner == null || cacheOwner.getScene() != getScene()) {
            cacheOwner = this;
            if (getScene() instanceof FXRuntime runtime) {
                for (IFXObject object : runtime.objects.values()) {
                    if (object instanceof FastCollisionEmitter emitter) { cacheOwner = emitter; break; }
                }
            }
        }
        long epoch = getLastTick();
        if (getScene() instanceof FXRuntime runtime && runtime.root instanceof FXObject root) epoch = root.getLastTick();
        currentFrame = cacheOwner.ownFrame;
        currentFrame.bind(getLevel(), epoch);
        if (sourceEntities == null && collision.entities && collision.sourceEntityExclusionRadius > 0) {
            var origin = getScene() instanceof FXRuntime runtime ? runtime.root.transform().position() : transform().position();
            sourceEntities = currentFrame.sourceEntities(new Vec3(origin.x, origin.y, origin.z), collision.sourceEntityExclusionRadius);
        }
    }

    public CollisionFrame frame() {
        if (currentFrame == null) prepareFrame();
        return currentFrame;
    }

    public long collisionTick() {
        return getScene() instanceof FXRuntime runtime && runtime.root instanceof FXObject root ? root.getLastTick() : getLastTick();
    }

    public boolean isSourceEntity(CollisionFrame.Target target) {
        return target.kind() == CollisionFrame.Kind.ENTITY && sourceEntities != null && sourceEntities.contains((int) target.id());
    }

    public int ownerId() {
        if (collision.ownerEntityId >= 0) return collision.ownerEntityId;
        return getEffectExecutor() instanceof EntityEffectExecutor executor ? executor.entity.getId() : -1;
    }

    public boolean recordHit(CollisionFrame.Hit hit, Vec3 point) {
        String group = collision.deduplicationGroup.isBlank() ? id().toString() : collision.deduplicationGroup;
        if (collision.deduplicateEmitterHits && !frame().remember(group, hit.candidate().target(), collision.contactCooldownTicks)) return false;
        hitCount++;
        recentHits.add(Map.of("kind", hit.candidate().target().kind().name(), "target", hit.candidate().target().id(), "position", List.of(point.x, point.y, point.z), "normal", List.of(hit.normal().x, hit.normal().y, hit.normal().z), "tick", collisionTick()));
        while (recentHits.size() > 128) recentHits.remove();
        return true;
    }

    @Override
    public void update(float dt) {
        long start = System.nanoTime();
        super.update(dt);
        simulationNanos += System.nanoTime() - start;
    }

    public Map<String, Object> collisionStatistics() {
        return Map.of("hits", hitCount, "simulationMilliseconds", simulationNanos / 1e6, "cache", currentFrame == null ? Map.of() : currentFrame.statistics(), "recentHits", List.copyOf(recentHits));
    }

    @Override
    public void reset() {
        super.reset();
        ownFrame.reset();
        currentFrame = null;
        sourceEntities = null;
        cacheOwner = null;
        recentHits.clear();
        hitCount = simulationNanos = 0;
    }
}
