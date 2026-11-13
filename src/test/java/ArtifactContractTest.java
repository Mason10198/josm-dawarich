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
      assertNotNull(jar.getEntry("org/openstreetmap/josm/plugins/dawarich/DawarichPlugin.class"));
      assertNotNull(jar.getEntry("images/dawarich.svg"));
      assertFalse(jar.stream().anyMatch(e -> e.getName().endsWith(".class") && e.getName().startsWith("org/openstreetmap/josm/") && !e.getName().equals("org/openstreetmap/josm/plugins/dawarich/DawarichPlugin.class")));
      assertFalse(jar.stream().anyMatch(e -> e.getName().startsWith("jakarta/json/") || e.getName().startsWith("javax/json/") || e.getName().startsWith("org/junit/")));
    }
    assertTrue(Files.isRegularFile(Path.of("LICENSE")));
  }
}
