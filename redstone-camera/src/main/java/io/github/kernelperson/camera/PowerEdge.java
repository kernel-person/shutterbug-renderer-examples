package io.github.kernelperson.camera;
final class PowerEdge {
    private boolean powered;
    PowerEdge(boolean initial) { powered=initial; }
    boolean sample(boolean current) {
        boolean rising=current&&!powered; powered=current; return rising;
    }
}
