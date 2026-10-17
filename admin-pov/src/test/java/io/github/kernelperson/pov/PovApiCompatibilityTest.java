package io.github.kernelperson.pov;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PovApiCompatibilityTest {
    @TempDir Path fixture;

    @Test void currentApiSupportsTargetExclusions() throws Exception {
        assertTrue(supported(PovCapture.class));
    }

    @Test void oldFourArgumentApiIsRejectedBeforeStartingThePlugin() throws Exception {
        Path source=fixture.resolve("ke/ric/renderer/api/CaptureRequest.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source,"""
                package ke.ric.renderer.api;
                public record CaptureRequest(org.bukkit.Location camera,double radius,
                    RenderSettings settings,int chunksPerTick) {}
                """);
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,
                "-classpath",System.getProperty("java.class.path"),"-d",fixture.toString(),source.toString()));
        URL compiled=PovCapture.class.getProtectionDomain().getCodeSource().getLocation();
        try(var loader=new URLClassLoader(new URL[]{fixture.toUri().toURL(),compiled},getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name,boolean resolve) throws ClassNotFoundException {
                if(name.equals("io.github.kernelperson.pov.PovCapture")||name.equals("ke.ric.renderer.api.CaptureRequest")) {
                    synchronized(getClassLoadingLock(name)) {
                        Class<?> type=findLoadedClass(name);
                        if(type==null)type=findClass(name);
                        if(resolve)resolveClass(type);
                        return type;
                    }
                }
                return super.loadClass(name,resolve);
            }
        }) {
            assertFalse(supported(loader.loadClass("io.github.kernelperson.pov.PovCapture")));
        }
    }

    private static boolean supported(Class<?> capture) throws Exception {
        var method=assertDoesNotThrow(()->capture.getDeclaredMethod("supportsTargetExclusions"));
        method.setAccessible(true);
        return (boolean)method.invoke(null);
    }
}
