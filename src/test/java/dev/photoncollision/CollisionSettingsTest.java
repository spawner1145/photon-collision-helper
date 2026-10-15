package dev.photoncollision;

import com.lowdragmc.lowdraglib2.configurator.annotation.Configurable;
import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.annotation.DefaultValue;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import com.google.gson.JsonParser;

import static org.junit.jupiter.api.Assertions.*;

final class CollisionSettingsTest {
    @Test void defaultConfigurationDoesNotEnableExtensionBehaviors() {
        var settings = new CollisionSettings();
        assertEquals(CollisionSettings.Backend.NATIVE, settings.backend);
        assertTrue(settings.blocks);
        assertFalse(settings.entities);
        assertFalse(settings.fluids);
        assertEquals(CollisionSettings.Response.INHERIT, settings.response);
        assertEquals(0, settings.sourceEntityExclusionRadius);
        assertEquals(0, settings.maxBounces);
        assertFalse(settings.deduplicateParticleHits);
        assertFalse(settings.deduplicateEmitterHits);
        assertFalse(settings.rayProbe);
        assertFalse(settings.detectStationaryOverlaps);
        assertFalse(settings.alignEffectsToNormal);
        assertTrue(settings.blockEffect.isEmpty());
        assertTrue(settings.entityEffect.isEmpty());
        assertTrue(settings.fluidEffect.isEmpty());
    }

    @Test void everyExposedCollisionSettingIsConfigurableAndPersisted() {
        for (var field : CollisionSettings.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
            assertTrue(Modifier.isPublic(field.getModifiers()), field.getName());
            assertTrue(field.isAnnotationPresent(Configurable.class), field.getName());
            assertTrue(field.isAnnotationPresent(Persisted.class), field.getName());
        }
    }

    @Test void everySettingHasAnEditorSectionAndLocalizedHelp() throws Exception {
        for (String language : new String[]{"en_us", "zh_cn"}) {
            try (var resource = CollisionSettings.class.getResourceAsStream("/assets/photon_collision_helper/lang/" + language + ".json")) {
                assertNotNull(resource);
                var translations = JsonParser.parseReader(new InputStreamReader(resource, StandardCharsets.UTF_8)).getAsJsonObject();
                assertTrue(translations.has("photon_collision_helper.editor.enable"));
                assertTrue(translations.has("photon_collision_helper.editor.native_entry.tips"));
                for (var field : CollisionSettings.class.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                    var annotation = field.getAnnotation(Configurable.class);
                    assertTrue(translations.has(annotation.name()), field.getName());
                    assertTrue(annotation.tips().length > 0, field.getName());
                    for (String tip : annotation.tips()) assertTrue(translations.has(tip), tip);
                    String section = CollisionSettings.sectionFor(field.getName());
                    if (!section.equals("backend")) assertTrue(translations.has("photon_collision_helper.editor.group." + section), section);
                    if (field.getType().isEnum()) {
                        for (Object value : field.getType().getEnumConstants()) assertTrue(translations.has(field.getType().getName() + "." + value));
                    }
                }
            }
        }
    }

    @Test void editorResetValuesAndNumericRangesMatchDefaults() throws Exception {
        var defaults = new CollisionSettings();
        for (var field : CollisionSettings.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
            var reset = field.getAnnotation(DefaultValue.class);
            assertNotNull(reset, field.getName());
            Object value = field.get(defaults);
            if (value instanceof Number number) {
                var range = field.getAnnotation(ConfigNumber.class);
                assertNotNull(range, field.getName());
                assertEquals(number.doubleValue(), reset.numberValue()[0], field.getName());
                assertTrue(number.doubleValue() >= range.range()[0] && number.doubleValue() <= range.range()[1], field.getName());
            } else if (value instanceof Boolean flag) {
                assertEquals(flag, reset.booleanValue()[0], field.getName());
            } else {
                assertEquals(value.toString(), reset.stringValue()[0], field.getName());
            }
        }
    }
}
