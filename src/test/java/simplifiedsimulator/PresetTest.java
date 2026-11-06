package simplifiedsimulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import simplifiedsimulator.sim.Simulation;

class PresetTest {

    @Test
    void presetsCoverTheRushHoursAndAStressTest() {
        assertEquals(7.5 * 3600, Preset.MORNING_RUSH.timeOfDay());
        assertEquals(1.0, Preset.MORNING_RUSH.demandScale());
        assertEquals(17 * 3600, Preset.EVENING_RUSH.timeOfDay());
        assertEquals(1.1, Preset.EVENING_RUSH.demandScale());
        assertEquals(8 * 3600, Preset.STRESS_TEST.timeOfDay());
        assertEquals(2.0, Preset.STRESS_TEST.demandScale());
    }

    @Test
    void applyingAPresetJumpsTheClockSetsTheTrafficAndRestartsTheStatistics() {
        Simulation sim = Simulation.createDefault();
        sim.run(900);
        assertTrue(sim.summary().motorCompleted() > 0);
        Preset.STRESS_TEST.apply(sim);
        assertEquals(8 * 3600, sim.timeOfDay(), 1e-6);
        assertEquals(2.0, sim.demandScale());
        assertEquals(0, sim.summary().motorCompleted(), "the score starts again");
        assertTrue(sim.agents().size() > 0, "road users already on the map stay");
    }

    @Test
    void presetsSitOnF5ToF7() {
        assertEquals(com.jme3.input.KeyInput.KEY_F5, Preset.MORNING_RUSH.key());
        assertEquals(com.jme3.input.KeyInput.KEY_F6, Preset.EVENING_RUSH.key());
        assertEquals(com.jme3.input.KeyInput.KEY_F7, Preset.STRESS_TEST.key());
        assertEquals("F5", Preset.MORNING_RUSH.keyName());
    }

    @Test
    void keyMappingsLeadBackToTheirPreset() {
        for (Preset p : Preset.values()) {
            assertEquals(java.util.Optional.of(p), Preset.forMapping(p.mapping()));
        }
        assertTrue(Preset.forMapping("Sim.Pause").isEmpty());
    }

    @Test
    void descriptionSaysWhatThePresetDoes() {
        assertEquals("Stress test: 08:00, traffic 200%", Preset.STRESS_TEST.description());
    }
}
