package io.github.kernelperson.camera;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PowerEdgeTest {
    @Test void continuousPowerTriggersOnlyOnceAndFallingEdgeArmsTheNextShot() {
        var edge=new PowerEdge(false);
        assertTrue(edge.sample(true)); assertFalse(edge.sample(true));
        assertFalse(edge.sample(false)); assertTrue(edge.sample(true));
    }
    @Test void loadingAnAlreadyPoweredCameraDoesNotTakeAnUnrequestedPhoto() {
        var edge=new PowerEdge(true);
        assertFalse(edge.sample(true));
        assertFalse(edge.sample(false)); assertTrue(edge.sample(true));
    }
}
