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
}
