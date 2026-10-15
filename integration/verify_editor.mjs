import assert from 'node:assert/strict';
import {readFile,writeFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {call} from '../../output/火龙卷/editor/mcp_call.mjs';

const root=fileURLToPath(new URL('../',import.meta.url));
const state=await call('photon_get_editor_state');
const types=await call('photon_list_object_types');
assert.ok(types.objects.includes('collision_emitter'));
await call('photon_describe_object_type',{type:'collision_emitter'});
await call('photon_open_project',{path:root+'/../output/须佐能乎_环斩冲击/susanoo_ring.fxproj'});
await call('photon_playback',{action:'seek',tick:10});
const result=(await call('photon_execute_code',{code:await readFile(root+'/integration/verify_editor.groovy','utf8'),record_history:false,reload:false})).result;
assert.ok(result.passed);
await writeFile(root+'/integration/editor-verification.json',JSON.stringify(result,null,2));
const name=(await call('photon_execute_code',{code:"return runtime.objects.values().find{it.name.startsWith('11 ')}.name",record_history:false,reload:false})).result;
await call('photon_select_object',{object:name});
console.log(JSON.stringify(result,null,2));
