import assert from 'node:assert/strict';
import {readFile,writeFile,copyFile,mkdir} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {call} from '../../output/火龙卷/editor/mcp_call.mjs';

const root=fileURLToPath(new URL('../',import.meta.url));
await call('photon_get_editor_state');
await call('photon_list_object_types');
await call('photon_describe_object_type',{type:'particle_emitter'});
const args={};
if(process.argv.includes('--candidate')){
    await mkdir(root+'/build/runtime-validation',{recursive:true});
    args.jar=root+'/build/runtime-validation/native-editor-'+Date.now()+'.jar';
    await copyFile(root+'/build/libs/photon-collision-helper-neoforge-1.21.1-0.1.4.jar',args.jar);
}
const result=(await call('photon_execute_code',{code:await readFile(root+'/integration/verify_native_editor.groovy','utf8'),args,record_history:false,reload:false})).result;
assert.ok(result.passed);
await writeFile(root+'/integration/native-editor-verification.json',JSON.stringify(result,null,2));
console.log(JSON.stringify(result,null,2));
