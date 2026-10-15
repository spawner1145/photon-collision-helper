import assert from 'node:assert/strict';
import {readFile,writeFile,copyFile,mkdir} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {call} from '../../output/火龙卷/editor/mcp_call.mjs';

const root=fileURLToPath(new URL('../',import.meta.url));
const execute=async(code,args={})=>(await call('photon_execute_code',{code,args,record_history:false,reload:false})).result;
if(process.argv.includes('--reload')){
    await call('photon_open_project',{path:root+'/../output/backups/须佐环斩_算法性能优化前_20261015_141539/须佐能乎_环斩冲击/susanoo_ring.fxproj'});
    await call('photon_playback',{action:'pause'});
    await mkdir(root+'/build/runtime-validation',{recursive:true});
    const jar=root+'/build/runtime-validation/helper-'+Date.now()+'.jar';
    await copyFile(root+'/build/libs/photon-collision-helper-neoforge-1.21.1-0.1.4.jar',jar);
    await execute(`collisionTestLoader=new java.net.URLClassLoader([new java.io.File(args.jar).toURI().toURL()] as java.net.URL[],com.lowdragmc.photon.client.gameobject.FXObject.class.classLoader);collisionTestType=collisionTestLoader.loadClass('dev.photoncollision.FastCollisionEmitter').getField('TYPE').get(null);com.lowdragmc.photon.PhotonRegistries.FX_OBJECTS.registerOrOverride('collision_emitter',collisionTestType);return true`,{jar});
}
await call('photon_open_project',{path:root+'/../output/须佐能乎_环斩冲击/susanoo_ring.fxproj'});
await call('photon_playback',{action:'pause'});
const types=await call('photon_list_object_types');
assert.ok(types.objects.includes('collision_emitter'));
const description=await call('photon_describe_object_type',{type:'collision_emitter'});
await writeFile(root+'/integration/mcp-schema.json',JSON.stringify(description,null,2));
const result=await execute(await readFile(root+'/integration/verify_frame.groovy','utf8'));
assert.ok(result.passed);
await writeFile(root+'/integration/frame-verification.json',JSON.stringify(result,null,2));
console.log(JSON.stringify(result,null,2));
const particles=await execute(await readFile(root+'/integration/verify_particles.groovy','utf8'));
assert.ok(particles.passed);
await writeFile(root+'/integration/particle-verification.json',JSON.stringify(particles,null,2));
console.log(JSON.stringify(particles,null,2));
