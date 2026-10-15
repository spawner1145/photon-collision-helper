import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup
import com.lowdragmc.lowdraglib2.configurator.ui.ValueConfigurator
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents
import com.lowdragmc.photon.PhotonRegistries
import com.lowdragmc.photon.client.fx.FX
import com.lowdragmc.photon.client.gameobject.EmptyFXObject
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter
import com.lowdragmc.photon.client.gameobject.emitter.data.number.NumberFunction
import com.lowdragmc.photon.gui.editor.FXEditor
import java.lang.reflect.Modifier

def helperClass
if(args.jar){
    nativeEditorTestLoader=new java.net.URLClassLoader([new File(args.jar).toURI().toURL()] as java.net.URL[],ParticleEmitter.class.classLoader)
    helperClass=nativeEditorTestLoader.loadClass('dev.photoncollision.NativeCollisionEditor')
}else{
    helperClass=ParticleEmitter.class.classLoader.loadClass('dev.photoncollision.NativeCollisionEditor')
}
def nativeType=PhotonRegistries.FX_OBJECTS.get('particle_emitter')
assert nativeType.is(ParticleEmitter.TYPE)
assert nativeType.create().class==ParticleEmitter
def source=new ParticleEmitter()
source.setName('Native Physics Panel Regression')
source.config.physics.setEnable(true)
source.config.physics.setHasCollision(true)
source.config.startSpeed=NumberFunction.constant(0)
source.config.emission.emissionRate=NumberFunction.constant(0)
source.transform().localPosition(new org.joml.Vector3f(1,2,3))
def child=new EmptyFXObject()
child.setName('Child retained')
child.transform()._setInternalParentID(source.id())
child.transform().localPosition(new org.joml.Vector3f(4,5,6))
def sibling=new EmptyFXObject()
sibling.setName('Sibling retained')
def testFX=new FX()
testFX.fxData.objects().addAll([sibling,source,child])
def testEditor=new FXEditor()
testEditor.runtime=testFX.createInternalRuntime()
testEditor.hierarchyView.loadFXRuntime(testEditor.runtime)
testEditor.sceneView.simulateTo(0)
testEditor.sceneView.particleManager.pause()
def provider=client.level.registryAccess()
def before=source.serializeWrapper().copy()
def order=testEditor.runtime.fxData.objects()*.id()
def timelineBefore=testEditor.runtime.fxData.timeline().serializeNBT(provider).copy()
def group=testEditor.inspectorView.inspect(source)
def sync=helperClass.getMethod('sync',FXEditor)
def physics=group.configurators.find{it.sourceField?.name=='physics'}
assert physics
3.times{sync.invoke(null,testEditor)}
def entries=physics.configurators.findAll{it.class.name.endsWith('NativeCollisionEditor$NativeEntry')}
assert entries.size()==1
assert source.serializeWrapper()==before
def button=entries[0].configurators[0].inlineContainer.children.find{it instanceof Button}
assert button
def click=Button.getDeclaredField('onClick')
click.accessible=true
click.get(button).handleEvent(UIEvent.create(UIEvents.MOUSE_DOWN))
def upgraded=testEditor.runtime.objects[source.id()]
assert upgraded.class.name=='dev.photoncollision.FastCollisionEmitter'
assert upgraded.name==source.name
assert upgraded.collision.backend.name()=='NATIVE'
assert upgraded.collision.blocks&&!upgraded.collision.entities&&!upgraded.collision.fluids
assert upgraded.serializeWrapper().getCompound('data').getCompound('config')==before.getCompound('data').getCompound('config')
assert upgraded.serializeWrapper().getCompound('data').getCompound('transform')==before.getCompound('data').getCompound('transform')
assert child.transform().parent().is(upgraded.transform())
assert child.transform().localPosition()==new org.joml.Vector3f(4,5,6)
assert testEditor.runtime.fxData.objects()*.id()==order
assert testEditor.runtime.fxData.timeline().serializeNBT(provider)==timelineBefore
assert testEditor.runtime.objects[sibling.id()].is(sibling)
assert !testEditor.sceneView.particleManager.playing
def roots=testEditor.inspectorView.inspector.scrollerView.viewContainer.children
def upgradedPhysics=roots[0].configurators.find{it.sourceField?.name=='physics'}
def extension=upgradedPhysics.configurators.find{it.sourceField?.name=='collision'}
assert extension
def settings=[]
def visit
visit={current->current.configurators.each{row->if(row.sourceField?.declaringClass==upgraded.collision.class)settings.add(row);if(row instanceof ConfiguratorGroup)visit(row)}}
visit(extension)
assert settings.size()==30
assert settings.every{it instanceof ValueConfigurator}
def entities=settings.find{it.sourceField.name=='entities'}
entities.toggle.setOn(true,true)
assert upgraded.collision.entities
testEditor.historyView.undo()
assert !upgraded.collision.entities
testEditor.historyView.redo()
assert upgraded.collision.entities
def restored=com.lowdragmc.photon.client.gameobject.IFXObject.deserializeWrapper(upgraded.serializeWrapper())
assert restored.collision.entities&&restored.id()==source.id()
testEditor.historyView.undo()
testEditor.historyView.undo()
testEditor.historyView.undo()
assert testEditor.runtime.objects[source.id()].is(source)
assert source.serializeWrapper()==before
assert child.transform().parent().is(source.transform())
testEditor.historyView.redo()
assert testEditor.runtime.objects[source.id()].is(upgraded)
assert child.transform().parent().is(upgraded.transform())
assert testEditor.runtime.fxData.objects()*.id()==order
assert PhotonRegistries.FX_OBJECTS.get('particle_emitter').is(nativeType)
testEditor.sceneView.reset()
testEditor.runtime.destroy(true)
return [passed:true,nativeEntryInPhysics:true,noDuplicateEntry:true,nativeRegistryUnchanged:true,untouchedUntilClick:true,buttonWorks:true,fieldsEditable:settings.size(),preservesConfigTransformChildrenOrderAndTimeline:true,nativeDefaultsPreserved:true,editUndoRedoVerified:true,conversionUndoRedoVerified:true,savedSettingsReloaded:true,realProjectUnchanged:true,validationLoader:helperClass.classLoader.class.name]
