import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

def type = com.lowdragmc.photon.PhotonRegistries.FX_OBJECTS.get('collision_emitter')
def loader = type.class.classLoader
def world = new TrackedDummyWorld()
def frame = loader.loadClass('dev.photoncollision.CollisionFrame').getConstructor().newInstance()
def settings = type.create().collision
settings.entities = false
def cases = []
def check = { String name, Closure body -> body(); cases.add(name) }
def query = { AABB bounds, Vec3 movement -> frame.nearest(bounds, movement, 1d, settings, -1, { false } as java.util.function.Predicate) }
def box = { double x, double y, double z -> new AABB(x-.1, y-.1, z-.1, x+.1, y+.1, z+.1) }
def fresh = { world = new TrackedDummyWorld(); frame.reset(); frame.bind(world, 1L) }
check('cached voxel shapes reused within tick and invalidated next tick') {
    world.setBlockAndUpdate(new BlockPos(5,0,0), Blocks.STONE.defaultBlockState())
    frame.bind(world, 1L)
    def hit = query(box(.5,.5,.5), new Vec3(10,0,0))
    assert hit != null && Math.abs(hit.time() - .44) < 1e-9
    long reads = frame.blockReads
    assert query(box(.5,.5,.5), new Vec3(10,0,0)) != null
    assert frame.blockReads == reads && frame.cacheHits > 0
    world.removeBlock(new BlockPos(5,0,0))
    frame.bind(world, 2L)
    assert query(box(.5,.5,.5), new Vec3(10,0,0)) == null
}
check('long diagonal DDA across negative coordinates and tile corners') {
    fresh()
    world.setBlockAndUpdate(new BlockPos(1000,0,1000), Blocks.STONE.defaultBlockState())
    frame.bind(world, 2L)
    def hit = query(box(-1000,.5,-1000), new Vec3(3000,0,3000))
    assert hit != null && Math.abs(hit.time() - 1999.9/3000) < 1e-8
    assert hit.candidate().target().id() == new BlockPos(1000,0,1000).asLong()
}
check('editor render-only block filter does not hide physical obstacles') {
    fresh()
    world.setBlockAndUpdate(new BlockPos(5,0,0), Blocks.STONE.defaultBlockState())
    world.setBlockFilter({false} as java.util.function.Predicate)
    frame.bind(world, 2L)
    assert query(box(.5,.5,.5), new Vec3(10,0,0)) != null
    world.setBlockFilter(null)
}
check('partial slabs fences and stairs preserve actual voxel bounds') {
    fresh()
    world.setBlockAndUpdate(new BlockPos(5,0,0), Blocks.STONE_SLAB.defaultBlockState())
    world.setBlockAndUpdate(new BlockPos(5,7,8), Blocks.OAK_FENCE.defaultBlockState())
    world.setBlockAndUpdate(new BlockPos(-9,0,0), Blocks.STONE_STAIRS.defaultBlockState())
    frame.bind(world, 2L)
    assert query(box(.5,.8,.5), new Vec3(10,0,0)) == null
    assert query(box(.5,.25,.5), new Vec3(10,0,0)) != null
    assert query(box(.5,8.25,8.5), new Vec3(10,0,0)) != null
    assert query(box(-15,.25,.5), new Vec3(10,0,0)) != null
}
check('fluids opt in independently of solid blocks') {
    fresh()
    world.setBlockAndUpdate(new BlockPos(5,0,0), Blocks.WATER.defaultBlockState())
    frame.bind(world, 2L)
    assert query(box(.5,.5,.5), new Vec3(10,0,0)) == null
    settings.blocks = false
    settings.fluids = true
    assert query(box(.5,.5,.5), new Vec3(10,0,0)).candidate().target().kind().name() == 'FLUID'
    settings.fluids = false
    settings.blocks = true
}
check('entities ownership living nonliving exact type filters') {
    fresh()
    def living = new ArmorStand(world, 5d, 0d, .5d)
    living.xOld=5d; living.yOld=0d; living.zOld=.5d
    world.addEntity(living)
    def item = new ItemEntity(world, 8d, .5d, .5d, new ItemStack(Items.STONE))
    item.xOld=8d; item.yOld=.5d; item.zOld=.5d
    world.addEntity(item)
    frame.bind(world, 2L)
    settings.blocks=false; settings.entities=true
    assert query(box(.5,.5,.5), new Vec3(10,0,0)).candidate().target().id() == living.id
    assert frame.nearest(box(.5,.5,.5), new Vec3(10,0,0), 1d, settings, living.id, {false} as java.util.function.Predicate) == null
    settings.entityFilter = Enum.valueOf(settings.entityFilter.class, 'NON_LIVING')
    assert query(box(.5,.5,.5), new Vec3(10,0,0)).candidate().target().id() == item.id
    settings.entityFilter = Enum.valueOf(settings.entityFilter.class, 'ALL')
    settings.excludedEntityTypes='minecraft:armor_stand'
    assert query(box(.5,.5,.5), new Vec3(10,0,0)).candidate().target().id() == item.id
    settings.includedEntityTypes='minecraft:armor_stand'
    assert query(box(.5,.5,.5), new Vec3(10,0,0)) == null
    settings.excludedEntityTypes=''
    assert query(box(.5,.5,.5), new Vec3(10,0,0)).candidate().target().id() == living.id
    settings.includedEntityTypes=''
    settings.entityFilter = Enum.valueOf(settings.entityFilter.class, 'PLAYERS')
    assert query(box(.5,.5,.5), new Vec3(10,0,0)) == null
    settings.entityFilter = Enum.valueOf(settings.entityFilter.class, 'LIVING')
}
check('moving entity continuous sweep and fractional timestep') {
    fresh()
    def living = new ArmorStand(world, 5d, 0d, -9.5d)
    living.xOld=5d; living.yOld=0d; living.zOld=10.5d
    world.addEntity(living)
    frame.bind(world, 2L)
    assert query(box(.5,.5,.5), new Vec3(10,0,0)) == null
    settings.sweepEntityMotion=true
    assert query(box(.5,.5,.5), new Vec3(10,0,0)) != null
    assert frame.nearest(box(5d,.5,.5), Vec3.ZERO, .5d, settings, -1, {false} as java.util.function.Predicate) != null
    settings.sweepEntityMotion=false
}
check('large entities overflow spatial index rather than disappear') {
    fresh()
    def living = new ArmorStand(world, 5d, 0d, .5d)
    living.xOld=5d; living.yOld=0d; living.zOld=.5d
    living.setBoundingBox(new AABB(5,-1000,-1000,6,1000,1000))
    world.addEntity(living)
    frame.bind(world, 2L)
    assert query(box(.5,.5,.5), new Vec3(10,0,0)) != null
}
check('shared dedup cooldown groups and world change') {
    fresh()
    settings.entities=false; settings.blocks=true
    world.setBlockAndUpdate(new BlockPos(5,0,0), Blocks.STONE.defaultBlockState())
    frame.bind(world, 2L)
    def target = query(box(.5,.5,.5), new Vec3(10,0,0)).candidate().target()
    assert frame.remember('blade', target, 2)
    assert !frame.remember('blade', target, 2)
    assert frame.remember('other', target, 2)
    frame.bind(world, 3L)
    assert !frame.remember('blade', target, 2)
    frame.bind(world, 4L)
    assert frame.remember('blade', target, 2)
    frame.bind(new TrackedDummyWorld(), 4L)
    assert frame.remember('blade', target, 2)
}
check('cache capacity overflow recomputes without dropping collisions') {
    fresh()
    settings.cachedTileLimit=1
    world.setBlockAndUpdate(new BlockPos(100,0,0), Blocks.STONE.defaultBlockState())
    frame.bind(world, 2L)
    assert query(box(.5,.5,.5), new Vec3(200,0,0)) != null
    assert frame.statistics().cachedTiles <= 1
}
check('block id allowlist and denylist') {
    settings.excludedBlockTypes='minecraft:stone'
    assert query(box(.5,.5,.5), new Vec3(200,0,0)) == null
    settings.excludedBlockTypes=''
    settings.includedBlockTypes='minecraft:stone'
    assert query(box(.5,.5,.5), new Vec3(200,0,0)) != null
    settings.includedBlockTypes='minecraft:dirt'
    assert query(box(.5,.5,.5), new Vec3(200,0,0)) == null
    settings.includedBlockTypes=''
}
check('proxy world chunk sections and proxy entity index') {
    fresh()
    world.setBlockAndUpdate(new BlockPos(5,0,0), Blocks.STONE.defaultBlockState())
    def proxy = new TrackedDummyWorld(world)
    frame.bind(proxy, 2L)
    assert query(box(.5,.5,.5), new Vec3(10,0,0)) != null
    world.removeBlock(new BlockPos(5,0,0))
    def living=new ArmorStand(world,5d,0d,.5d)
    living.xOld=5d;living.yOld=0d;living.zOld=.5d
    world.addEntity(living)
    settings.entities=true
    frame.bind(proxy, 3L)
    assert query(box(.5,.5,.5), new Vec3(10,0,0)).candidate().target().kind().name()=='ENTITY'
    settings.entities=false
}
check('seeded spatial index agrees with brute force exact sweeps') {
    fresh()
    settings.cachedTileLimit=16384
    def random=new java.util.Random(817341)
    def positions=[]
    100.times{index->
        def position=new BlockPos(random.nextInt(65)-32,random.nextInt(12)-4,random.nextInt(65)-32)
        world.setBlockAndUpdate(position,[Blocks.STONE,Blocks.STONE_SLAB,Blocks.STONE_STAIRS,Blocks.OAK_FENCE][index%4].defaultBlockState())
        positions.add(position)
    }
    def colliders=positions.toSet().collectMany{position->world.getBlockState(position).getCollisionShape(world,position,net.minecraft.world.phys.shapes.CollisionContext.empty()).toAabbs().collect{it.move(position)}}
    def sweep=loader.loadClass('dev.photoncollision.SweepMath').getMethod('sweep',AABB,Vec3,AABB)
    frame.bind(world,2L)
    160.times{index->
        def start=new Vec3(random.nextDouble()*100-50,random.nextDouble()*20-8,random.nextDouble()*100-50)
        def bounds=index%3==0?new AABB(start,start):box(start.x,start.y,start.z)
        def movement=index%2==0?Vec3.atCenterOf(positions[index%positions.size()]).subtract(start).scale(2):new Vec3(random.nextDouble()*300-150,random.nextDouble()*60-30,random.nextDouble()*300-150)
        def expected=colliders.collect{sweep.invoke(null,bounds,movement,it)}.findAll{it!=null}.collect{it.time()}.min()
        def actual=query(bounds,movement)?.time()
        assert expected==null?actual==null:actual!=null&&Math.abs(expected-actual)<1e-8
    }
}
check('native codec roundtrip includes collision settings') {
    def emitter=type.create()
    emitter.collision.fluids=true
    emitter.collision.response=Enum.valueOf(emitter.collision.response.class,'PIERCE')
    emitter.collision.detectStationaryOverlaps=true
    def serialized=emitter.serializeNBT(world.registryAccess())
    def restored=type.create()
    restored.deserializeNBT(world.registryAccess(), serialized)
    assert restored.collision.fluids && restored.collision.response.name()=='PIERCE'
    assert restored.collision.detectStationaryOverlaps
    assert restored.serializeNBT(world.registryAccess()).getCompound('collision') == serialized.getCompound('collision')
}
world.clear()
return [passed:true, cases:cases, realWorldUnchanged:true]
