import assert from 'node:assert/strict';
import {readFile,writeFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {call} from '../../output/火龙卷/editor/mcp_call.mjs';

const root=fileURLToPath(new URL('../',import.meta.url));
const project=root+'/../output/须佐能乎_环斩冲击/susanoo_ring.fxproj';
const execute=async(code,args={})=>(await call('photon_execute_code',{code,args,record_history:false,reload:false})).result;
const report={passed:false,cases:[],realWorldUnchanged:true};
try{
    for(const kind of ['default-block-only','old-behavior','source-only','source-moves-out','source-and-far','nearby-target','source-block']){
        await call('photon_open_project',{path:project});
        await call('photon_set_view',{scene_mode:'PLATFORM'});
        await call('photon_playback',{action:'seek',tick:0});
        const result=await execute(`
            def world=editor.sceneView.level
            assert world instanceof com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld
            world.getAllRenderedEntities().findAll{it!=null}.toList().each{it.discard()}
            world.clear()
            scene.setRenderedCore([])
            world.setBlockFilter(null)
            editor.sceneView.simulateTo(0)
            runtime.root.updatePos(new org.joml.Vector3f(.5f,2f,.5f))
            def source=new net.minecraft.world.entity.decoration.ArmorStand(world,.7469d,2.435d,.9059d)
            source.xOld=source.x;source.yOld=source.y;source.zOld=source.z
            world.addEntity(source)
            def target=null
            if(args.kind=='source-and-far'||args.kind=='nearby-target'){
                target=new net.minecraft.world.entity.decoration.ArmorStand(world,.5d,4d,args.kind=='source-and-far'?50.5d:3.2d)
                target.xOld=target.x;target.yOld=target.y;target.zOld=target.z
                world.addEntity(target)
            }
            if(args.kind=='source-block')world.setBlockAndUpdate(new net.minecraft.core.BlockPos(0,3,0),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState())
            def probes=runtime.objects.values().findAll{it.name.startsWith('11 ')}
            assert probes.every{it.collision.metaClass.hasProperty(it.collision,'sourceEntityExclusionRadius')}
            assert probes.every{!it.collision.entities&&it.collision.sourceEntityExclusionRadius==0d}
            if(args.kind!='default-block-only')probes.each{it.collision.entities=true;it.collision.sourceEntityExclusionRadius=1.25d}
            if(args.kind=='old-behavior')probes.each{it.collision.sourceEntityExclusionRadius=0d}
            def samples=[]
            120.times{step->
                if(args.kind=='source-moves-out'&&step==3){source.setPos(.5d,4d,70.5d);source.xOld=source.x;source.yOld=source.y;source.zOld=source.z}
                editor.sceneView.particleManager.tickInternal()
                def actors=editor.sceneView.particleManager.particlesByRenderType().values().flatten().findAll{it instanceof com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter}
                def children=actors.findAll{it.name.startsWith('21 ')}
                samples.add([tick:step+1,pending:children.size(),active:children.sum{it.particleAmount}?:0,origins:children.collect{def position=it.scene.root.transform().position();[position.x,position.y,position.z]}])
            }
            def hits=probes.collectMany{it.collisionStatistics().recentHits}
            assert source.alive&&source.health==source.maxHealth
            return [sourceId:source.id,targetId:target?.id,hits:hits,samples:samples]`,{kind});
        const contact=result.samples.find(sample=>sample.pending);
        const blast=result.samples.find(sample=>sample.active);
        if(['default-block-only','source-only','source-moves-out'].includes(kind)){
            assert.equal(result.hits.length,0,'Source entity creates a ghost contact: '+kind);
            assert.ok(!contact&&!blast);
        }else{
            assert.equal(result.hits.length,1);
            assert.ok(contact&&blast);
            assert.equal(blast.tick-contact.tick,50);
            if(kind==='old-behavior')assert.equal(result.hits[0].target,result.sourceId);
            else if(kind==='source-block')assert.equal(result.hits[0].kind,'BLOCK','Source immunity must never hide blocks');
            else{
                assert.equal(result.hits[0].target,result.targetId);
                assert.notEqual(result.hits[0].target,result.sourceId);
                if(kind==='source-and-far')assert.ok(result.samples.every(sample=>sample.origins.every(position=>Math.hypot(position[0]-.5,position[2]-.5)>40)));
            }
        }
        report.cases.push({kind,...result});
        console.log(kind+': '+(blast?'blast at tick '+blast.tick:'no ghost blast'));
    }
    report.passed=true;
}finally{
    await execute('editor.sceneView.level.getAllRenderedEntities().findAll{it!=null}.toList().each{it.discard()};editor.sceneView.loadScene();return true');
    await call('photon_open_project',{path:project});
    await call('photon_playback',{action:'seek',tick:10});
}
await writeFile(root+'/integration/source-contact-verification.json',JSON.stringify(report,null,2));
