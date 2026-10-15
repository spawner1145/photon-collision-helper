import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld
import com.lowdragmc.photon.client.FXSceneOptions
import com.lowdragmc.photon.client.PhotonParticleManager
import com.lowdragmc.photon.client.fx.BlockEffectExecutor
import com.lowdragmc.photon.client.fx.EntityEffectExecutor
import com.lowdragmc.photon.client.fx.FX
import com.lowdragmc.photon.client.fx.FXHelper
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction3
import com.lowdragmc.photon.client.gameobject.emitter.data.shape.Dot
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.block.Blocks
import net.minecraft.client.player.RemotePlayer
import org.joml.Vector3f

assert client.getOverlay()==null
def type=com.lowdragmc.photon.PhotonRegistries.FX_OBJECTS.get('collision_emitter')
def loader=type.class.classLoader
assert loader.class.name=='cpw.mods.modlauncher.TransformingClassLoader'
def particleClass=loader.loadClass('dev.photoncollision.FastCollisionParticle')
def frameClass=loader.loadClass('dev.photoncollision.CollisionFrame')
def pendingField=ParticleEmitter.getDeclaredField('pendingSubEmitterSpawns')
pendingField.accessible=true
def provider=client.level.registryAccess()
def definitions=[]
def settingsTests=[]
def actualProjectileTests=[]
def makeEntity={world,kind->
    def entity=kind=='player'?new RemotePlayer(client.level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),'CollisionTest'))
        :kind=='mob'?EntityType.ZOMBIE.create(world)
        :kind=='boat'?EntityType.BOAT.create(world)
        :new net.minecraft.world.entity.item.ItemEntity(world,5d,.25d,.5d,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE))
    entity.setPos(5d,0d,.5d)
    entity.xOld=entity.x;entity.yOld=entity.y;entity.zOld=entity.z
    world.addEntity(entity)
    return entity
}
def active={objects->objects.findAll{it instanceof ParticleEmitter&&it.config.physics.enable&&it.config.physics.hasCollision}}
for(def descriptor:args.definitions){
    def definition=FXHelper.getFX(ResourceLocation.parse(descriptor.id),false)
    assert definition!=null:descriptor.id
    def emitters=active(definition.fxData.objects())
    assert emitters.size()==descriptor.emitters:descriptor.id
    def copied=active(definition.createRuntime().fxData.objects())
    assert copied.every{it.class.name=='dev.photoncollision.FastCollisionEmitter'}
    for(def emitter:emitters){
        assert emitter.class.name=='dev.photoncollision.FastCollisionEmitter'
        def settings=emitter.collision
        assert settings.backend.name()=='CACHED_SWEEP'&&settings.blocks&&!settings.fluids
        assert settings.entities==descriptor.entities
        if(descriptor.entities)assert settings.entityFilter.name()=='LIVING'&&settings.ignoreOwner&&settings.sweepEntityMotion
        for(def kind:['block','player','mob','item','boat','owner','empty']){
            def world=new TrackedDummyWorld()
            def frame=frameClass.getConstructor().newInstance()
            def entity=null
            if(kind=='block')world.setBlockAndUpdate(new BlockPos(5,0,0),Blocks.STONE.defaultBlockState())
            else if(!(kind in ['empty']))entity=makeEntity(world,kind=='owner'?'player':kind)
            frame.bind(world,1L)
            def hit=frame.nearest(new AABB(.4d,.4d,.4d,.6d,.6d,.6d),new Vec3(10d,0d,0d),1d,settings,kind=='owner'?entity.id:-1,{false} as java.util.function.Predicate)
            def expected=kind=='block'||descriptor.entities&&kind in ['player','mob']
            assert (hit!=null)==expected:[descriptor.id,emitter.name,kind,hit]
            if(hit)assert hit.candidate().target().kind().name()==(kind=='block'?'BLOCK':'ENTITY')
            settingsTests.add([fx:descriptor.id,emitter:emitter.name,target:kind,hit:hit!=null])
            frame.reset()
        }
    }
    if(descriptor.entities){
        def source=emitters.find{it.config.subEmitters.enable&&it.config.subEmitters.emitters.any{it.event.name()=='FirstCollision'}}
        assert source
        for(def kind:['player','mob','item','block','owner']){
            def emitter=source.copy(true)
            def world=new TrackedDummyWorld()
            def manager=new PhotonParticleManager(FXSceneOptions.DEFAULT)
            world.setParticleManager(manager)
            manager.setLevel(world)
            def entity=kind=='block'?null:makeEntity(world,kind=='owner'?'player':kind)
            if(kind=='block')world.setBlockAndUpdate(new BlockPos(5,0,0),Blocks.STONE.defaultBlockState())
            def executor=kind=='owner'?new EntityEffectExecutor(new FX(),world,entity,EntityEffectExecutor.AutoRotate.NONE)
                :new BlockEffectExecutor(new FX(),world,new BlockPos(0,0,0))
            emitter.setEffect(executor)
            emitter.setLevel(world)
            emitter.transform().localPosition(new Vector3f())
            emitter.transform().localRotation(new org.joml.Quaternionf())
            emitter.transform().localScale(new Vector3f(1))
            emitter.config.shape.shape=new Dot()
            emitter.config.startSize=new NumberFunction3(.2f,.2f,.2f)
            emitter.config.startSpeed=NumberFunction.constant(0)
            emitter.config.startLifetime=NumberFunction.constant(100)
            emitter.config.velocityOverLifetime.setEnable(false)
            emitter.config.sizeOverLifetime.setEnable(false)
            emitter.config.noise.setEnable(false)
            emitter.config.physics.setGravity(NumberFunction.constant(0))
            emitter.config.physics.setFriction(NumberFunction.constant(1))
            def health=entity instanceof net.minecraft.world.entity.LivingEntity?entity.health:null
            def particle=particleClass.constructors[0].newInstance(emitter,emitter.config)
            particle.setSimPos(new Vector3f(.5f,.5f,.5f),true)
            particle.setInternalVelocity(new Vector3f(10,0,0))
            particle.updateTick(1f)
            def shouldHit=kind in ['player','mob','block']
            assert emitter.collisionStatistics().hits==(shouldHit?1:0):[descriptor.id,kind,emitter.collisionStatistics()]
            assert particle.removed==shouldHit
            def queue=pendingField.get(emitter)
            int queued=queue.size()
            while(!queue.isEmpty())queue.poll().run()
            manager.tickInternal()
            def spawned=manager.particlesByRenderType().values().flatten().findAll{it instanceof com.lowdragmc.photon.client.gameobject.FXObject}
            def event=emitter.config.subEmitters.emitters.find{it.event.name()=='FirstCollision'}
            def impact=FXHelper.getFX(event.fxLocation)
            assert impact!=null
            def expectedNames=impact.fxData.objects()*.name.toSet()
            def scenes=spawned*.getScene().findAll{it!=null}.unique()
            def impacts=scenes.findAll{it.fxData.objects()*.name.toSet()==expectedNames}
            assert impacts.size()==(shouldHit?1:0):[descriptor.id,kind,impacts.size(),spawned*.name]
            if(shouldHit){
                def contact=emitter.collisionStatistics().recentHits[0]
                def position=impacts[0].root.transform().position()
                assert Math.abs(position.x-contact.position[0])<.0001
            }
            if(health!=null)assert entity.health==health&&entity.alive
            actualProjectileTests.add([fx:descriptor.id,target:kind,hit:shouldHit,impactCount:impacts.size(),queuedEffects:queued,noDamage:true])
            scenes.each{it.destroy(true)}
            manager.clear()
            emitter.reset()
        }
    }
    definitions.add([id:descriptor.id,collisionEmitters:emitters.size(),entities:descriptor.entities])
}
return [passed:true,definitions:definitions,settingsCases:settingsTests.size(),settingsTests:settingsTests,actualProjectileTests:actualProjectileTests,playersAndMobsVerified:true,itemsAndVehiclesExcluded:true,ownerExcluded:true,blockOnlyEffectsPreserved:true,actualFirstCollisionChildrenSpawned:true,noRealWorldModification:true,validationLoader:loader.class.name]
