package simplifiedsimulator.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProtocolCodecTest {

    @Test
    void encodesSensorMessage() {
        assertEquals("{\"sensorstate\":{\"id\":12}}", ProtocolCodec.encodeSensor(12));
    }

    @Test
    void decodesLightMessage() {
        assertEquals(Optional.of(new LightUpdate(5, LightState.GREEN)),
                ProtocolCodec.decodeLight("{\"lightstate\":{\"id\":5,\"state\":2}}"));
    }

    @Test
    void decodesKeysCaseInsensitively() {
        assertEquals(Optional.of(new LightUpdate(51, LightState.ORANGE)),
                ProtocolCodec.decodeLight("{ \"LightState\": { \"Id\": 51, \"State\": 1 } }"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "hello",
        "{\"lightstate\":{\"id\":5}}",
        "{\"lightstate\":{\"state\":0}}",
        "{\"lightstate\":{\"id\":5,\"state\":7}}",
        "{\"lightstate\":{\"id\":\"five\",\"state\":0}}",
        "{\"sensorstate\":{\"id\":5}}",
        "[1, 2, 3]"
    })
    void ignoresInvalidMessages(String message) {
        assertTrue(ProtocolCodec.decodeLight(message).isEmpty(), message);
    }

    @Test
    void lightStateCodesRoundTrip() {
        for (LightState state : LightState.values()) {
            assertEquals(state, LightState.fromCode(state.code()));
        }
    }

    @Test
    void decodesTheNameAControllerAnnounces() {
        assertEquals(java.util.Optional.of("Smart"), ProtocolCodec.decodeName("{\"controllername\":{\"name\":\"Smart\"}}"));
        assertEquals(java.util.Optional.of("2014 controller"),
                ProtocolCodec.decodeName("{\"ControllerName\":{\"Name\":\"  2014 controller \"}}"));
    }

    @Test
    void ignoresNamesThatAreMissingTooLongOrNotPlainText() {
        for (String bad : new String[] {"{\"controllername\":{}}", "{\"controllername\":{\"name\":\"\"}}",
                "{\"controllername\":{\"name\":\"a name that is much too long\"}}",
                "{\"controllername\":{\"name\":\"café\"}}", "{\"controllername\":{\"name\":7}}",
                "{\"controllername\":\"x\"}", "{\"lightstate\":{\"id\":2,\"state\":2}}", "not json", "[]"}) {
            assertTrue(ProtocolCodec.decodeName(bad).isEmpty(), bad);
        }
    }

    @Test
    void encodesTheClockForLockstep() {
        assertEquals("{\"clock\":{\"time\":12.3}}", ProtocolCodec.encodeClock(12.3));
    }

    @Test
    void decodesTheControllersClockReply() {
        assertEquals(java.util.OptionalDouble.of(12.3), ProtocolCodec.decodeClockDone("{\"clockdone\":{\"time\":12.3}}"));
        assertEquals(java.util.OptionalDouble.of(4.0), ProtocolCodec.decodeClockDone("{\"ClockDone\":{\"Time\":4}}"));
        for (String bad : new String[] {"{\"clockdone\":{}}", "{\"clockdone\":{\"time\":\"x\"}}", "not json",
                "{\"lightstate\":{\"id\":2,\"state\":2}}"}) {
            assertTrue(ProtocolCodec.decodeClockDone(bad).isEmpty(), bad);
        }
    }

    @Test
    void encodesALoopBecomingFree() {
        assertEquals("{\"sensorfree\":{\"id\":3}}", ProtocolCodec.encodeSensorFree(3));
    }

    @Test
    void decodesTheFeaturesAControllerAsksFor() {
        assertEquals(java.util.Optional.of(java.util.Set.of("sensorfree")),
                ProtocolCodec.decodeFeatures("{\"Features\":[\"SensorFree\"]}"));
        assertEquals(java.util.Optional.of(java.util.Set.of()), ProtocolCodec.decodeFeatures("{\"features\":[]}"));
        for (String bad : new String[] {"{\"features\":\"sensorfree\"}", "{\"features\":[3]}", "nope",
                "{\"sensorstate\":{\"id\":2}}"}) {
            assertTrue(ProtocolCodec.decodeFeatures(bad).isEmpty(), bad);
        }
    }
}
