import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Dot
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import org.joml.Vector3f

def type = com.lowdragmc.photon.PhotonRegistries.FX_OBJECTS.get('collision_emitter')
def particleClass = type.class.classLoader.loadClass('dev.photoncollision.FastCollisionParticle')
def world = new TrackedDummyWorld()
def cases = []
def make = { String response ->
    def emitter=type.create()
    emitter.setLevel(world)
    emitter.collision.backend=Enum.valueOf(emitter.collision.backend.class,'CACHED_SWEEP')
    emitter.collision.deduplicateParticleHits=true
    emitter.config.physics.setEnable(true)
    emitter.config.physics.setHasCollision(true)
    emitter.config.shape.shape=new Dot()
    emitter.config.startSize=new NumberFunction3(.2f,.2f,.2f)
    emitter.config.startSpeed=NumberFunction.constant(0)
    emitter.config.startLifetime=NumberFunction.constant(20)
    emitter.config.physics.setGravity(NumberFunction.constant(0))
    emitter.config.physics.setFriction(NumberFunction.constant(1))
    emitter.collision.entities=false
    emitter.collision.response=Enum.valueOf(emitter.collision.response.class,response)
    emitter.collision.restitution=1
    def particle=particleClass.constructors[0].newInstance(emitter,emitter.config)
    particle.setSimPos(new Vector3f(.5f,.5f,.5f),true)
    particle.setInternalVelocity(new Vector3f(10f,0f,0f))
    [emitter,particle]
}
def check = { String name, Closure body -> body(); cases.add(name) }
check('default helper retains native config and creates the actual Photon particle class') {
    def emitter=type.create()
    def reference=new com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter()
    assert emitter.config.serializeNBT(world.registryAccess())==reference.config.serializeNBT(world.registryAccess())
    assert emitter.collision.backend.name()=='NATIVE'&&!emitter.collision.entities&&!emitter.collision.fluids
    emitter.setLevel(world)
    def create=emitter.class.getDeclaredMethod('createNewParticle')
    create.accessible=true
    def nativeParticle=create.invoke(emitter)
    assert nativeParticle.class==com.lowdragmc.photon.client.gameobject.particle.TileParticle
    assert emitter.collisionStatistics().cache.isEmpty()
}
world.setBlockAndUpdate(new BlockPos(5,0,0),Blocks.STONE.defaultBlockState())
check('REMOVE stops at exact surface and emits one hit') {
    def (emitter,particle)=make('REMOVE')
    particle.updateTick(1f)
    assert particle.removed && emitter.collisionStatistics().hits==1
    assert Math.abs(particle.getWorldPos(1).x-4.9f)<1e-5
    assert Math.abs(emitter.collisionStatistics().recentHits[0].position[0]-5)<1e-5
}
check('STOP collision dedup never allows falling through the same surface') {
    def (emitter,particle)=make('STOP')
    5.times {
        particle.setInternalVelocity(new Vector3f(10f,0f,0f))
        particle.updateTick(1f)
        assert Math.abs(particle.getWorldPos(1).x-4.9f)<1e-5
    }
    assert !particle.removed && emitter.collisionStatistics().hits==1
}
check('BOUNCE reflects remainder and stored velocity') {
    def (emitter,particle)=make('BOUNCE')
    particle.updateTick(1f)
    assert !particle.removed && particle.getWorldPos(1).x<0
    assert particle.getInternalVelocity().x<0
    assert emitter.collisionStatistics().hits==1
}
check('INHERIT uses native removal flag') {
    def (emitter,particle)=make('INHERIT')
    emitter.config.physics.setRemovedWhenCollided(true)
    particle.updateTick(1f)
    assert particle.removed && emitter.collisionStatistics().hits==1
}
check('SLIDE preserves tangential movement') {
    def (emitter,particle)=make('SLIDE')
    particle.setInternalVelocity(new Vector3f(10f,0f,1f))
    particle.updateTick(1f)
    assert Math.abs(particle.getWorldPos(1).x-4.9f)<1e-4
    assert Math.abs(particle.getWorldPos(1).z-1.5f)<1e-4
    assert particle.getInternalVelocity().x==0 && particle.getInternalVelocity().z==1
}
world.setBlockAndUpdate(new BlockPos(8,0,0),Blocks.STONE.defaultBlockState())
check('PIERCE visits multiple surfaces in chronological order') {
    def (emitter,particle)=make('PIERCE')
    particle.updateTick(1f)
    assert !particle.removed && Math.abs(particle.getWorldPos(1).x-10.5f)<1e-5
    assert emitter.collisionStatistics().hits==2
    assert emitter.collisionStatistics().recentHits*.position.collect{it[0]}==[5d,8d]
}
check('contact budget stops untested movement rather than tunneling') {
    def (emitter,particle)=make('PIERCE')
    emitter.collision.maxHitsPerStep=1
    particle.updateTick(1f)
    assert Math.abs(particle.getWorldPos(1).x-4.9f)<1e-5
    assert emitter.collisionStatistics().hits==1
}
check('stationary overlap supports sensor volumes') {
    def (emitter,particle)=make('PIERCE')
    emitter.collision.detectStationaryOverlaps=true
    particle.setSimPos(new Vector3f(5.5f,.5f,.5f),true)
    particle.setInternalVelocity(new Vector3f())
    particle.updateTick(1f)
    assert emitter.collisionStatistics().hits==1
}
check('high speed projectile never hits the native velocity skip path') {
    def (emitter,particle)=make('REMOVE')
    particle.setInternalVelocity(new Vector3f(10000f,0f,0f))
    particle.updateTick(1f)
    assert particle.removed && Math.abs(particle.getWorldPos(1).x-4.9f)<1e-4
}
world.clear()
return [passed:true,cases:cases,realWorldUnchanged:true]
