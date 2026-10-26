package net.aerulion.taerra.storage;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mariadb.MariaDBContainer;

@Testcontainers(disabledWithoutDocker = true)
class MariaDbTileStoreTest extends SqlTileStoreContract {

  @Container static final MariaDBContainer DATABASE = new MariaDBContainer("mariadb:11.4");

  @Override
  JdbcDatabaseContainer<?> database() {
    return DATABASE;
  }

  @Override
  String type() {
    return "mariadb";
  }
}
