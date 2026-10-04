package com.arena.core.config;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import liquibase.integration.spring.SpringLiquibase;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DatabaseSchemaConfiguration {
  @Bean
  static BeanPostProcessor databaseSchemaInitializer(Environment environment) {
    return new SchemaInitializer(environment.getRequiredProperty("ARENA_DB_SCHEMA"));
  }

  /** Liquibase needs its schema before it can create DATABASECHANGELOG/LOCK. */
  static final class SchemaInitializer implements BeanPostProcessor {
    private final String schema;

    SchemaInitializer(String schema) {
      if (schema == null || !schema.matches("[a-z][a-z0-9_]{0,62}")) {
        throw new IllegalArgumentException("ARENA_DB_SCHEMA must be a nonblank lowercase PostgreSQL identifier (maximum 63 characters)");
      }
      this.schema = schema;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
      if (bean instanceof SpringLiquibase liquibase) {
        if (!schema.equals(liquibase.getDefaultSchema())
            || (liquibase.getLiquibaseSchema() != null && !schema.equals(liquibase.getLiquibaseSchema()))) {
          throw new BeanCreationException(beanName, "Liquibase schemas must match ARENA_DB_SCHEMA");
        }
        try (Connection connection = liquibase.getDataSource().getConnection();
             Statement statement = connection.createStatement()) {
          statement.execute("CREATE SCHEMA IF NOT EXISTS \"" + schema + "\"");
        } catch (SQLException exception) {
          throw new BeanCreationException(beanName, "Cannot initialize configured database schema", exception);
        }
      }
      return bean;
    }
  }
}
