import assert from 'node:assert/strict';
import {readFile,writeFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {call} from '../../output/火龙卷/editor/mcp_call.mjs';

const root=fileURLToPath(new URL('../',import.meta.url));
const inventory=JSON.parse(await readFile(root+'/integration/output-collision-inventory.json','utf8'));
const entityEffects=new Set(['豪火灭却','豪火球之术','豪龙火','须佐能乎_勾玉']);
const definitions=inventory.filter(entry=>entry.entry).map(entry=>({
    id:entry.entry.replace(/^assets\/([^/]+)\/fx\//,'$1:').replace(/\.fx$/,''),
    entities:entityEffects.has(entry.effect),emitters:entry.rows.length,
}));
const results=[];
for(const descriptor of definitions){
    const result=(await call('photon_execute_code',{
        code:await readFile(root+'/integration/verify_output_collisions.groovy','utf8'),
        args:{definitions:[descriptor]},record_history:false,reload:false,
    })).result;
    assert.ok(result.passed);
    results.push(result);
    console.log(descriptor.id+': '+result.settingsCases+' filter checks, '+result.actualProjectileTests.length+' projectile checks passed');
}
const report={passed:true,definitions:results.flatMap(result=>result.definitions),
    settingsCases:results.reduce((sum,result)=>sum+result.settingsCases,0),
    projectileTests:results.flatMap(result=>result.actualProjectileTests),
    validationLoader:'cpw.mods.modlauncher.TransformingClassLoader',
    noRealWorldModification:true,normalizedTrajectoryForProjectileChecks:true};
assert.equal(report.definitions.reduce((sum,entry)=>sum+entry.collisionEmitters,0),41);
await writeFile(root+'/integration/output-collision-runtime-verification.json',JSON.stringify(report,null,2));
console.log(JSON.stringify({passed:true,definitions:report.definitions.length,filterCases:report.settingsCases,projectileCases:report.projectileTests.length}));
