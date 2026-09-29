package io.github.kernelperson.camera;

import java.util.Properties;

/** Generated public artwork contract, shared by optical and cosmetic placement. */
final class CameraModelContract {
    private static final Properties VALUES=new Properties();
    static {
        try(var in=CameraModelContract.class.getResourceAsStream("/camera-model.properties")) {
            if(in==null) throw new IllegalStateException("Missing camera placement contract");
            VALUES.load(in);
        } catch(Exception failure) {throw new ExceptionInInitializerError(failure);}
    }
    static final double BODY_HALF_EXTENT=value("body-half-extent");
    static final double BODY_SCALE=value("body-scale");
    static final double SOCKET_GAP=value("socket-gap");
    static final double LENS_FRONT=value("lens-front");
    static final double CLEARANCE=value("capture-clearance");
    private static double value(String key) {
        double value=Double.parseDouble(VALUES.getProperty(key));
        if(!Double.isFinite(value)||value<=0) throw new IllegalStateException("Invalid camera placement contract");
        return value;
    }
}
