package com.arena.core.config;

import jakarta.persistence.EntityManagerFactory;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Runs against disposable databases provisioned by deploy/validate-db-schema.sh. */
@EnabledIfEnvironmentVariable(named = "ARENA_SCHEMA_TEST_DB_URL", matches = ".+")
class DatabaseSchemaStartupTest {
  @Configuration(proxyBeanMethods = false)
  @EntityScan("com.arena.core")
  static class Entities {}

  @TempDir Path legacyResources;

  private ApplicationContextRunner runner(String profile, String schema) {
    return new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,
            LiquibaseAutoConfiguration.class, HibernateJpaAutoConfiguration.class))
        .withUserConfiguration(DatabaseSchemaConfiguration.class, Entities.class)
        .withPropertyValues("spring.profiles.active=" + profile,
            "ARENA_DB_SCHEMA=" + schema, "ARENA_DB_NAME=" + schema,
            "ARENA_DB_HOST=127.0.0.1", "ARENA_DB_USERNAME=validation", "ARENA_DB_PASSWORD=validation",
            "spring.datasource.url=" + System.getenv("ARENA_SCHEMA_TEST_DB_URL") + schema,
            "spring.datasource.username=validation", "spring.datasource.password=validation",
            "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false");
  }

  @Test void existingArenaTrackingAndDataRemainCompatible() throws Exception {
    // Reconstruct the prior changelog in temporary files, never duplicate migrations in Git.
    Path source = Path.of(getClass().getResource("/db/changelog/db.changelog-master.xml").toURI()).getParent();
    Path target = legacyResources.resolve("db/changelog");
    try (var files = Files.walk(source)) {
      for (Path file : files.filter(Files::isRegularFile).toList()) {
        Path output = target.resolve(source.relativize(file));
        Files.createDirectories(output.getParent());
        Files.writeString(output, Files.readString(file).replace("${ARENA_DB_SCHEMA}", "arena"));
      }
    }
    var dataSource = new DriverManagerDataSource(System.getenv("ARENA_SCHEMA_TEST_DB_URL") + "arena_legacy", "validation", "validation");
    var jdbc = new JdbcTemplate(dataSource);
    // This local fixture simulates the previously deployed schema and tracking tables.
    jdbc.execute("CREATE SCHEMA arena");
    try (var loader = new URLClassLoader(new URL[] {legacyResources.toUri().toURL()}, null)) {
      var legacy = new SpringLiquibase();
      legacy.setResourceLoader(new DefaultResourceLoader(loader));
      legacy.setDataSource(dataSource); legacy.setDefaultSchema("arena");
      legacy.setChangeLog("classpath:db/changelog/db.changelog-master.xml");
      legacy.afterPropertiesSet();
    }
    jdbc.execute("CREATE TABLE arena.compatibility_probe (value integer)");
    jdbc.execute("INSERT INTO arena.compatibility_probe VALUES (42)");
    int count = jdbc.queryForObject("SELECT count(*) FROM arena.databasechangelog", Integer.class);
    runner("prod", "arena")
        .withPropertyValues("spring.datasource.url=" + System.getenv("ARENA_SCHEMA_TEST_DB_URL") + "arena_legacy")
        .run(context -> {
          assertNull(context.getStartupFailure(), () -> "Existing arena migration failed: " + context.getStartupFailure());
          assertEquals(count, jdbc.queryForObject("SELECT count(*) FROM arena.databasechangelog", Integer.class));
          assertEquals(42, jdbc.queryForObject("SELECT value FROM arena.compatibility_probe", Integer.class));
        });
  }

  @Test void freshAndRepeatedStartupForEveryEnvironment() {
    for (String[] mapping : new String[][] {{"dev", "arena_dev"}, {"sit", "arena_sit"}, {"prod", "arena"}}) {
      String profile = mapping[0], schema = mapping[1];
      for (int startup = 0; startup < 2; startup++) {
        runner(profile, schema).run(context -> {
          assertNull(context.getStartupFailure(), () -> "Startup failed for " + profile + ": " + context.getStartupFailure());
          assertEquals(schema, context.getEnvironment().getProperty("spring.liquibase.default-schema"));
          assertEquals(schema, context.getEnvironment().getProperty("spring.liquibase.parameters.ARENA_DB_SCHEMA"));
          assertEquals(schema, context.getBean(EntityManagerFactory.class).getProperties().get("hibernate.default_schema"));
          JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
          assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM pg_namespace WHERE nspname = ?", Integer.class, schema));
          assertTrue(jdbc.queryForObject("SELECT count(*) FROM " + schema + ".databasechangelog", Integer.class) > 10);
          assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM " + schema + ".databasechangeloglock WHERE locked", Integer.class));
          assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ('customer','billing_order','databasechangelog')", Integer.class));
          jdbc.queryForObject("SELECT count(*) FROM " + schema + ".billing_order", Integer.class);
        });
      }
    }
  }
}
