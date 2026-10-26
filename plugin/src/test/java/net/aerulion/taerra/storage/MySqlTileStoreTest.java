package net.aerulion.taerra.storage;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers(disabledWithoutDocker = true)
class MySqlTileStoreTest extends SqlTileStoreContract {

  @Container static final MySQLContainer DATABASE = new MySQLContainer("mysql:8.4");

  @Override
  JdbcDatabaseContainer<?> database() {
    return DATABASE;
  }

  @Override
  String type() {
    return "mysql";
  }
}
