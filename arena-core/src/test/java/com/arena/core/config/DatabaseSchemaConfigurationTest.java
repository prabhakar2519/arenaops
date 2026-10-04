package com.arena.core.config;

import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseSchemaConfigurationTest {
  @Test void requiresSchemaPropertyBeforeDatabaseStartup() {
    assertThrows(IllegalStateException.class, () -> DatabaseSchemaConfiguration.databaseSchemaInitializer(new MockEnvironment()));
    assertThrows(IllegalArgumentException.class, () -> DatabaseSchemaConfiguration.databaseSchemaInitializer(
        new MockEnvironment().withProperty("ARENA_DB_SCHEMA", " ")));
  }

  @Test void rejectsMissingBlankAndUnsafeSchemaNames() {
    for (String schema : new String[] {null, "", " ", "arena;DROP SCHEMA public", "Arena", "x".repeat(64)}) {
      assertThrows(IllegalArgumentException.class, () -> new DatabaseSchemaConfiguration.SchemaInitializer(schema));
    }
  }

  @Test void bootstrapsBeforeLiquibaseUsingConfiguredSchema() throws Exception {
    for (String schema : new String[] {"arena_dev", "arena_sit", "arena"}) {
      DataSource source = mock(DataSource.class);
      Connection connection = mock(Connection.class);
      Statement statement = mock(Statement.class);
      when(source.getConnection()).thenReturn(connection);
      when(connection.createStatement()).thenReturn(statement);
      SpringLiquibase liquibase = new SpringLiquibase();
      liquibase.setDataSource(source); liquibase.setDefaultSchema(schema);
      var initializer = new DatabaseSchemaConfiguration.SchemaInitializer(schema);
      assertSame(liquibase, initializer.postProcessBeforeInitialization(liquibase, "liquibase"));
      verify(statement).execute("CREATE SCHEMA IF NOT EXISTS \"" + schema + "\"");
      verify(connection).close();
    }
  }

  @Test void rejectsConflictingTrackingAndDefaultSchema() {
    SpringLiquibase liquibase = new SpringLiquibase();
    var initializer = new DatabaseSchemaConfiguration.SchemaInitializer("arena_sit");
    liquibase.setDefaultSchema("arena");
    assertThrows(BeanCreationException.class, () -> initializer.postProcessBeforeInitialization(liquibase, "liquibase"));
    liquibase.setDefaultSchema("arena_sit"); liquibase.setLiquibaseSchema("public");
    assertThrows(BeanCreationException.class, () -> initializer.postProcessBeforeInitialization(liquibase, "liquibase"));
  }
}
