package dev.photoncollision;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.configurator.EditAction;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleConfig;
import com.lowdragmc.photon.client.gameobject.emitter.particle.ParticleEmitter;
import com.lowdragmc.photon.gui.editor.FXEditor;
import com.lowdragmc.photon.gui.editor.view.FXObjectTreeNode;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;

public final class NativeCollisionEditor {
    private static boolean registered;

    private NativeCollisionEditor() {}

    public static void register() {
        if (registered) return;
        NeoForge.EVENT_BUS.addListener(NativeCollisionEditor::onClientTick);
        registered = true;
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        var ui = ModularUI.of(Minecraft.getInstance().screen);
        if (ui == null) return;
        for (var editor : ui.getElementsByType(FXEditor.class)) {
            if (editor.isDisplayed() && editor.isVisible()) sync(editor);
        }
    }

    public static void sync(FXEditor editor) {
        var inspector = editor.inspectorView.inspector;
        if (!(inspector.getInspectedConfigurable() instanceof ParticleEmitter emitter)
                || emitter.getClass() != ParticleEmitter.class
                || editor.runtime == null || editor.runtime.objects.get(emitter.id()) != emitter) return;
        for (var child : inspector.scrollerView.viewContainer.getChildren()) {
            if (!(child instanceof ConfiguratorGroup root)) continue;
            var physics = physicsGroup(root);
            if (physics == null || physics.getConfigurators().stream().anyMatch(NativeEntry.class::isInstance)) continue;
            physics.addConfigurator(new NativeEntry(editor, emitter));
        }
    }

    private static ConfiguratorGroup physicsGroup(ConfiguratorGroup root) {
        for (var row : root.getConfigurators()) {
            if (!(row instanceof ConfiguratorGroup group)) continue;
            var field = group.getSourceField();
            if (field != null && field.getDeclaringClass() == ParticleConfig.class && field.getName().equals("physics")) return group;
            var nested = physicsGroup(group);
            if (nested != null) return nested;
        }
        return null;
    }

    public static FastCollisionEmitter enable(FXEditor editor, ParticleEmitter source) {
        var runtime = editor.runtime;
        if (runtime == null || runtime.objects.get(source.id()) != source || source.getClass() != ParticleEmitter.class) {
            throw new IllegalStateException("Select an authored native particle emitter in the current project");
        }
        var replacement = new FastCollisionEmitter();
        replacement.deserializeNBT(Platform.getFrozenRegistry(), source.serializeNBT(Platform.getFrozenRegistry()).copy());
        editor.historyView.pushHistory(Component.translatable("photon_collision_helper.editor.enable"), EditAction.of(
                () -> replace(editor, source, replacement),
                () -> replace(editor, replacement, source)));
        var root = editor.inspectorView.inspect(replacement);
        var physics = physicsGroup(root);
        if (physics != null) {
            physics.setCollapse(false);
            for (var row : physics.getConfigurators()) {
                if (row instanceof ConfiguratorGroup group && row.getSourceField() != null
                        && row.getSourceField().getName().equals("collision")) group.setCollapse(false);
            }
        }
        return replacement;
    }

    private static void replace(FXEditor editor, ParticleEmitter source, ParticleEmitter replacement) {
        var runtime = editor.runtime;
        if (runtime == null || runtime.objects.get(source.id()) != source) {
            throw new IllegalStateException("Emitter or project changed while applying collision settings");
        }
        int index = runtime.fxData.objects().indexOf(source);
        if (index < 0) throw new IllegalStateException("Emitter is not part of the authored effect");
        var parent = source.transform().parent();
        var children = new ArrayList<>(source.transform().children());
        int sibling = source.transform().getSiblingIndex();
        long time = editor.sceneView.particleManager.getTime();
        editor.sceneView.reset();
        editor.inspectorView.clear();
        editor.sceneView.sceneEditor.setTransformGizmoTarget(null);
        for (var child : children) child.parent(null, false);
        source.setScene(null);
        runtime.fxData.objects().set(index, replacement);
        replacement.transform()._setInternalParentID(parent == null ? null : parent.id());
        replacement.setScene(runtime);
        for (var child : children) child.parent(replacement.transform(), false);
        replacement.transform().setSiblingIndex(sibling);
        editor.hierarchyView.loadFXRuntime(runtime);
        editor.timelineView.rebuild();
        var node = nodeOf(editor.hierarchyView.getRootNode(), replacement);
        if (node != null) {
            editor.hierarchyView.treeList.expandNodeAlongPath(node);
            editor.hierarchyView.treeList.setSelected(List.of(node), true);
        }
        editor.sceneView.sceneEditor.setTransformGizmoTarget(replacement.transform());
        editor.sceneView.simulateTo(0);
        editor.sceneView.requestSimulateTo(time);
    }

    private static FXObjectTreeNode nodeOf(FXObjectTreeNode root, ParticleEmitter emitter) {
        if (root.getKey() == emitter) return root;
        for (var child : root.getChildren()) {
            var result = nodeOf(child, emitter);
            if (result != null) return result;
        }
        return null;
    }

    private static final class NativeEntry extends ConfiguratorGroup {
        private NativeEntry(FXEditor editor, ParticleEmitter emitter) {
            super("photon_collision_helper.editor.title", false);
            setTips("photon_collision_helper.editor.native_entry.tips");
            var row = new Configurator("");
            var button = new Button().setText("photon_collision_helper.editor.enable")
                    .setOnClick(event -> enable(editor, emitter));
            button.layout(layout -> layout.widthPercent(100));
            row.inlineContainer.addChild(button);
            addConfigurator(row);
        }
    }
}
