package com.arena.core.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Configuration;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies actual Spring config loading in isolated working directories without starting application services. */
class LocalEnvironmentConfigTest {
  @TempDir Path directory;

  /** Proves module and repository-root launches find the same local settings and exported values override them. */
  @Test void devLoadsRootFileFromEitherDirectoryAndEnvironmentTakesPrecedence() throws Exception {
    Files.writeString(directory.resolve(".env"), "ARENA_DB_PASSWORD=file-test-password\nKC_BFF_CLIENT_SECRET=file-test-secret\n");
    Path module = Files.createDirectory(directory.resolve("arena-core"));
    runProbe(module, "dev", false);
    runProbe(directory, "dev", false);
    runProbe(module, "dev", true);
  }

  /** Keeps the production profile isolated from a local secret file even if one exists beside the application. */
  @Test void remoteProfilesDoNotImportLocalEnvironmentFile() throws Exception {
    Files.writeString(directory.resolve(".env"), "ARENA_DB_PASSWORD=file-test-password\nKC_BFF_CLIENT_SECRET=file-test-secret\n");
    runProbe(directory, "sit", false);
    runProbe(directory, "prod", false);
  }

  /** Runs a minimal child JVM so file lookup uses the same working-directory rules as a Maven launch. */
  private void runProbe(Path workingDirectory, String profile, boolean override) throws Exception {
    Path output = directory.resolve("probe-output.txt");
    ProcessBuilder builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
        "-cp", System.getProperty("java.class.path"), Probe.class.getName(), profile, Boolean.toString(override));
    builder.directory(workingDirectory.toFile()).redirectErrorStream(true).redirectOutput(output.toFile());
    builder.environment().remove("ARENA_DB_PASSWORD");
    builder.environment().remove("KC_BFF_CLIENT_SECRET");
    builder.environment().remove("KC_REALM");
    if (override) builder.environment().put("ARENA_DB_PASSWORD", "exported-test-password");
    Process process = builder.start();
    try {
      assertTrue(process.waitFor(20, TimeUnit.SECONDS), "Configuration probe timed out");
      assertEquals(0, process.exitValue(), "Configuration probe failed: " + Files.readString(output));
    } finally { process.destroyForcibly(); }
  }

  /** Empty configuration allows ConfigData validation without database, email, or Keycloak side effects. */
  @Configuration(proxyBeanMethods = false)
  public static class Probe {
    /** Checks fake credentials in the loaded environment and never prints their values. */
    public static void main(String[] arguments) {
      String profile = arguments[0];
      SpringApplication application = new SpringApplication(Probe.class);
      try (var context = application.run("--spring.profiles.active=" + profile,
          "--spring.config.location=classpath:application.yaml", "--spring.main.web-application-type=none",
          "--spring.main.banner-mode=off", "--logging.level.root=OFF", "--logging.level.com.arena=OFF")) {
        var environment = context.getEnvironment();
        String expectedRealm = profile.equals("prod") ? "arena" : profile.equals("sit") ? "arena-sit" : "arena-dev";
        if (!expectedRealm.equals(environment.getProperty("keycloak-admin.realm")))
          throw new IllegalStateException("Profile selected an incorrect realm");
        if (profile.equals("prod") || profile.equals("sit")) {
          if (environment.getProperty("ARENA_DB_PASSWORD") != null || environment.getProperty("KC_BFF_CLIENT_SECRET") != null)
            throw new IllegalStateException("Production unexpectedly imported local configuration");
        } else {
          String expected = Boolean.parseBoolean(arguments[1]) ? "exported-test-password" : "file-test-password";
          if (!expected.equals(environment.getProperty("spring.datasource.password"))
              || !"file-test-secret".equals(environment.getProperty("keycloak-admin.client-secret")))
            throw new IllegalStateException("Local credentials did not resolve from the expected source");
        }
      }
    }
  }
}
