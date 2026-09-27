package io.github.kernelperson.postcards;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkGateTest {
    @Test void permitsOnlyOneActiveRequestAndEnforcesCooldown() {
        var gate = new WorkGate<String>(2, 5);
        long first = gate.begin("player", 10);
        assertNotEquals(0, first);
        assertEquals(0, gate.begin("player", 100)); // still busy, despite elapsed cooldown
        gate.finish("player", first);
        long next = gate.begin("player", 100);
        assertNotEquals(0, next);
        gate.finish("player", next);
        assertEquals(0, gate.begin("player", 104));
        assertNotEquals(0, gate.begin("player", 105));
    }
    @Test void staleCompletionCannotReleaseReplacementRequest() {
        var gate = new WorkGate<String>(1, 0);
        long old = gate.begin("camera", 0);
        gate.invalidate("camera");
        long fresh = gate.begin("camera", 1);
        gate.finish("camera", old);
        assertFalse(gate.current("camera", old));
        assertTrue(gate.current("camera", fresh));
        assertEquals(0, gate.begin("other", 2));
        gate.clear();
        assertFalse(gate.current("camera", fresh));
        assertNotEquals(0, gate.begin("other", 3));
    }
    @Test void boundsGlobalWorkWithoutBlockingAnUnrelatedRequestAfterFinish() {
        var gate = new WorkGate<String>(1, 5);
        long first = gate.begin("one", 1);
        assertEquals(0, gate.begin("two", 2));
        gate.finish("one", first);
        assertNotEquals(0, gate.begin("two", 3));
    }
}
