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
        Files.writeString(output, Files.readString(file).replace("${ARENA_DB_SCHEMA}", "arena")
            // Exclude all academy/session additions when reconstructing the pre-academy deployment.
            .replaceAll("(?m)^[ \t]*<include file=\"db/changelog/features/R1_1[0-8]_[^\"]+\"/>", ""));
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
    jdbc.execute("INSERT INTO arena.customer (id,customer_name,parlour_name,sports,onboarding_date,trial_starts_at,trial_ends_at,subscription_type,billing_amount,billing_start_date,next_due_date,payment_status,status,access_allowed) VALUES (nextval('arena.seq_customer'),'Legacy club','Legacy club','SINGLE',CURRENT_DATE,CURRENT_DATE,CURRENT_DATE,'MONTHLY',0,CURRENT_DATE,CURRENT_DATE,'NOT_DUE','ACTIVE',true)");
    int count = jdbc.queryForObject("SELECT count(*) FROM arena.databasechangelog", Integer.class);
    runner("prod", "arena")
        .withPropertyValues("spring.datasource.url=" + System.getenv("ARENA_SCHEMA_TEST_DB_URL") + "arena_legacy")
        .run(context -> {
          assertNull(context.getStartupFailure(), () -> "Existing arena migration failed: " + context.getStartupFailure());
          assertEquals(count + 18, jdbc.queryForObject("SELECT count(*) FROM arena.databasechangelog", Integer.class));
          assertEquals("SINGLE", jdbc.queryForObject("SELECT sports FROM arena.customer WHERE customer_name = 'Legacy club'", String.class));
          assertEquals(42, jdbc.queryForObject("SELECT count(*) FROM arena.sport_catalogue", Integer.class));
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
          jdbc.queryForObject("SELECT count(*) FROM " + schema + ".facility_code_counter", Integer.class);
          jdbc.queryForObject("SELECT count(*) FROM " + schema + ".facility_operation", Integer.class);
          assertEquals(42, jdbc.queryForObject("SELECT count(*) FROM " + schema + ".sport_catalogue", Integer.class));
          jdbc.queryForObject("SELECT count(*) FROM " + schema + ".training_batch", Integer.class);
          assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema = ? AND table_name = 'academy_staff' AND column_name IN ('app_user_id','email') AND is_nullable = 'YES'", Integer.class, schema));
        });
      }
    }
  }
}
