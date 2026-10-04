package com.arena.login.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.Configuration;
import static org.junit.jupiter.api.Assertions.*;

class EnvironmentProfilesTest {
  @TempDir Path directory;

  @Test
  void profilesResolveRealmEndpointsAndOnlyDevImportsLocalSecrets() throws Exception {
    Files.writeString(directory.resolve(".env"), "LOCAL_SECRET_PROBE=local-only\n");
    for (var profile : Map.of("dev", "arena-dev", "sit", "arena-sit", "prod", "arena").entrySet()) {
      Path output = directory.resolve(profile.getKey() + ".log");
      var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-cp",
          System.getProperty("java.class.path"), Probe.class.getName(), profile.getKey(), profile.getValue());
      builder.directory(directory.toFile()).redirectErrorStream(true).redirectOutput(output.toFile());
      var env = builder.environment();
      env.remove("KC_REALM"); env.remove("LOCAL_SECRET_PROBE");
      env.put("KC_BASE_URL", "http://keycloak:8080/auth");
      env.put("KC_AUTH_URL", "https://ui.test/auth");
      env.put("KC_JWK_SET_URI", "http://keycloak:8080/auth/realms/" + profile.getValue() + "/protocol/openid-connect/certs");
      env.put("APP_BASE_URL", "https://ui.test");
      var process = builder.start();
      try {
        assertTrue(process.waitFor(20, TimeUnit.SECONDS), "Profile probe timed out");
        assertEquals(0, process.exitValue(), Files.readString(output));
      } finally { process.destroyForcibly(); }
    }
  }

  @Configuration(proxyBeanMethods = false)
  public static class Probe {
    public static void main(String[] args) {
      var app = new SpringApplication(Probe.class);
      try (var context = app.run("--spring.profiles.active=" + args[0], "--spring.config.location=classpath:application.yaml",
          "--spring.main.web-application-type=none", "--spring.main.banner-mode=off", "--logging.level.root=OFF")) {
        var env = context.getEnvironment();
        if (!args[1].equals(env.getProperty("keycloak-admin.realm"))) throw new IllegalStateException("Wrong realm");
        String tokenUri = env.getProperty("spring.security.oauth2.client.provider.keycloak.token-uri");
        if (!("http://keycloak:8080/auth/realms/" + args[1] + "/protocol/openid-connect/token").equals(tokenUri))
          throw new IllegalStateException("Wrong token endpoint");
        if ((env.getProperty("LOCAL_SECRET_PROBE") != null) != args[0].equals("dev"))
          throw new IllegalStateException("Remote profile imported local secret file");
        if (!args[0].equals("dev") && !Boolean.TRUE.equals(env.getProperty("server.servlet.session.cookie.secure", Boolean.class)))
          throw new IllegalStateException("Remote cookie is not secure");
      }
    }
  }
}
