import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.file.*;
import java.util.jar.*;
import org.junit.jupiter.api.Test;

class ArtifactContractTest {
  @Test void builtJarIsThinAndHasPluginContract() throws Exception {
    Path jarPath=Path.of("dist/dawarich.jar");
    assertTrue(Files.isRegularFile(jarPath), "run ant dist first");
    try (JarFile jar=new JarFile(jarPath.toFile())) {
      var m=jar.getManifest().getMainAttributes();
      assertEquals("dawarich",m.getValue("Plugin-Id"));
      assertEquals("org.openstreetmap.josm.plugins.dawarich.DawarichPlugin",m.getValue("Plugin-Class"));
      assertEquals("0.1.0",m.getValue("Plugin-Version"));
      assertEquals("19678",m.getValue("Plugin-Mainversion"));
      assertEquals("17",m.getValue("Plugin-Minimum-Java-Version"));
      assertEquals("Thin private GPS reference connector",m.getValue("Plugin-Description"));
      assertEquals("https://github.com/Mason10198/josm-dawarich",m.getValue("Plugin-Link"));
      assertEquals("images/dawarich.svg",m.getValue("Plugin-Icon"));
      assertNotNull(jar.getEntry("org/openstreetmap/josm/plugins/dawarich/DawarichPlugin.class"));
      assertNotNull(jar.getEntry("images/dawarich.svg"));
      assertNotNull(jar.getEntry("META-INF/LICENSE"));
      assertFalse(jar.stream().anyMatch(e -> e.getName().endsWith(".class") && e.getName().startsWith("org/openstreetmap/josm/") && !e.getName().startsWith("org/openstreetmap/josm/plugins/dawarich/")));
      assertFalse(jar.stream().anyMatch(e -> e.getName().startsWith("jakarta/json/") || e.getName().startsWith("javax/json/") || e.getName().startsWith("org/junit/")));
    }
    assertTrue(Files.isRegularFile(Path.of("LICENSE")));
  }
}
