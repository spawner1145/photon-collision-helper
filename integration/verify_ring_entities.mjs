import assert from 'node:assert/strict';
import {writeFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {call} from '../../output/火龙卷/editor/mcp_call.mjs';

const root=fileURLToPath(new URL('../',import.meta.url));
const project=root+'/../output/须佐能乎_环斩冲击/susanoo_ring.fxproj';
const execute=async(code,args={})=>(await call('photon_execute_code',{code,args,record_history:false,reload:false})).result;
const report={passed:false,cases:[]};
try{
    for(const kind of ['default-living','living','owner','nonliving','above-blade']){
        await call('photon_open_project',{path:project});
        await call('photon_set_view',{scene_mode:'PLATFORM'});
        await call('photon_playback',{action:'seek',tick:0});
        const result=await execute(`
            def world=editor.sceneView.level
            assert world instanceof com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld
            world.clear()
            scene.setRenderedCore([])
            editor.sceneView.simulateTo(0)
            runtime.root.updatePos(new org.joml.Vector3f(.5f,2f,.5f))
            def probes=runtime.objects.values().findAll{it.name.startsWith('11 ')}
            assert probes.every{!it.collision.entities&&!it.collision.fluids&&it.collision.response.name()=='INHERIT'}
            if(args.kind!='default-living')probes.each{it.collision.entities=true}
            def target=args.kind=='nonliving'?new net.minecraft.world.entity.item.ItemEntity(world,.5d,5d,50.5d,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE)):new net.minecraft.world.entity.decoration.ArmorStand(world,.5d,args.kind=='above-blade'?20d:4d,50.5d)
            target.xOld=target.x;target.yOld=target.y;target.zOld=target.z
            world.addEntity(target)
            if(args.kind=='owner')runtime.objects.values().findAll{it.name.startsWith('11 ')}.each{it.collision.ownerEntityId=target.id}
            def health=target instanceof net.minecraft.world.entity.LivingEntity?target.health:null
            def samples=[]
            120.times{step->
                editor.sceneView.particleManager.tickInternal()
                def children=editor.sceneView.particleManager.particlesByRenderType().values().flatten().findAll{it instanceof com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter}
                def pending=children.findAll{it.name.startsWith('21 ')}
                samples.add([tick:step+1,contacts:pending.size(),blasts:pending.count{it.particleAmount>0},stones:children.findAll{it.name.startsWith('25 ')||it.name.startsWith('26 ')||it.name.startsWith('27 ')}.sum{it.particleAmount}?:0])
            }
            def hits=runtime.objects.values().findAll{it.name.startsWith('11 ')}.collectMany{it.collisionStatistics().recentHits}
            assert target.alive && (!(target instanceof net.minecraft.world.entity.LivingEntity)||target.health==health)
            return [hits:hits,samples:samples,noGameplayDamage:true]`,{kind});
        const contact=result.samples.find(sample=>sample.contacts);
        const blast=result.samples.find(sample=>sample.blasts);
        if(kind==='living'){
            assert.ok(contact&&blast);
            assert.equal(blast.tick-contact.tick,50);
            assert.equal(result.hits.length,1,'Multiple adjacent probes duplicate one entity event');
            assert.equal(result.hits[0].kind,'ENTITY');
            assert.equal(Math.max(...result.samples.map(sample=>sample.stones)),3);
        }else{
            assert.ok(!contact&&!blast);
            assert.equal(result.hits.length,0);
        }
        report.cases.push({kind,...result});
        console.log(kind+': '+(contact?.tick??'no collision'));
    }
    report.passed=true;
}finally{
    await execute('editor.sceneView.loadScene();return true');
    await call('photon_open_project',{path:project});
    await call('photon_playback',{action:'seek',tick:10});
}
await writeFile(root+'/integration/ring-entity-verification.json',JSON.stringify(report,null,2));
