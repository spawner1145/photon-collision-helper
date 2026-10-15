import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup
import com.lowdragmc.lowdraglib2.configurator.ui.ValueConfigurator
import com.lowdragmc.lowdraglib2.configurator.ui.SearchComponentConfigurator
import com.lowdragmc.lowdraglib2.gui.ui.elements.Inspector
import com.lowdragmc.lowdraglib2.gui.ui.utils.HistoryStack
import java.lang.reflect.Modifier

def type=com.lowdragmc.photon.PhotonRegistries.FX_OBJECTS.get('collision_emitter')
def target=type.create()
target.config.physics.setEnable(true)
def history=new HistoryStack()
def inspector=new Inspector()
inspector.setHistoryStack(history)
def group=inspector.inspect(target)
def nodes=[]
def visit
visit={ConfiguratorGroup current,List path->
    current.configurators.each{row->
        def field=row.sourceField
        def next=path+[field?.name?:row.getLabel().string]
        nodes.add([row:row,field:field,path:next])
        if(row instanceof ConfiguratorGroup)visit(row,next)
    }
}
visit(group,[])
def extension=nodes.findAll{it.field?.name=='collision'&&it.field.declaringClass==target.class}
assert extension.size()==1
assert extension[0].path[-2]=='physics'
def settings=nodes.findAll{it.field?.declaringClass==target.collision.class}
def expected=target.collision.class.declaredFields.findAll{!Modifier.isStatic(it.modifiers)&&!it.synthetic}*.name.toSet()
assert settings*.field*.name.toSet()==expected
assert settings.size()==expected.size()
assert settings.every{it.row instanceof ValueConfigurator}
def rows=settings.collectEntries{[(it.field.name):it.row]}
assert ['blockEffect','entityEffect','fluidEffect'].every{rows[it] instanceof SearchComponentConfigurator}
def initialConfig=target.collision
rows.entities.toggle.setOn(true,true)
assert target.collision.entities
assert history.undoStack.size()>=2
history.undo()
assert !target.collision.entities
history.redo()
assert target.collision.entities&&target.collision.is(initialConfig)
def update=ValueConfigurator.getDeclaredMethod('updateValueActively',Object)
update.accessible=true
def cached=Enum.valueOf(target.collision.backend.class,'CACHED_SWEEP')
update.invoke(rows.backend,cached)
rows.contactCooldownTicks.textField.setText('7',true)
update.invoke(rows.deduplicationGroup,'editor_roundtrip')
update.invoke(rows.entityEffect,'susanoo_ring:cut_impact')
assert target.collision.backend==cached&&target.collision.contactCooldownTicks==7
assert target.collision.deduplicationGroup=='editor_roundtrip'
def serialized=target.serializeNBT(client.level.registryAccess())
def restored=type.create()
restored.deserializeNBT(client.level.registryAccess(),serialized)
assert restored.collision.entities&&restored.collision.backend==cached
assert restored.collision.contactCooldownTicks==7&&restored.collision.deduplicationGroup=='editor_roundtrip'
assert restored.collision.entityEffect=='susanoo_ring:cut_impact'
assert serialized.getCompound('collision').getString('backend')=='CACHED_SWEEP'
assert !serialized.getCompound('config').getCompound('physics').contains('collision')
def candidates=[]
rows.entityEffect.search('susanoo_ring',{value->candidates.add(value)} as com.lowdragmc.lowdraglib2.utils.search.IResultHandler)
assert candidates.contains('susanoo_ring:cut_impact')
return [passed:true,physicsNestingVerified:true,allFieldsEditable:expected.size(),undoRedoVerified:true,booleanNumberEnumStringAndFXVerified:true,persistencePathUnchanged:true,effectSearchVerified:true,rows:settings.collect{[field:it.field.name,widget:it.row.class.simpleName,path:it.path,label:it.row.getLabel().string]}]
