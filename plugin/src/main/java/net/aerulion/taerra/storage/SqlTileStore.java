package net.aerulion.taerra.storage;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import de.chojo.sadu.datasource.DataSourceCreator;
import de.chojo.sadu.mapper.RowMapperRegistry;
import de.chojo.sadu.mariadb.databases.MariaDb;
import de.chojo.sadu.mariadb.mapper.MariaDbMapper;
import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import de.chojo.sadu.updater.SqlUpdater;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;
import net.aerulion.taerra.model.MapRotation;
import net.aerulion.taerra.model.MapView;
import net.aerulion.taerra.model.TileCoordinate;
import net.aerulion.taerra.rendering.sample.FogView;
import net.aerulion.taerra.util.Throwables;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SqlTileStore implements TileStore {

  private static final int DELETE_BATCH = 1000;
  private static final int COPY_BATCH = 200;
  private static final long CONNECT_MILLIS = 10_000;
  private static final int CLOSE_WAITS = 50;
  private static final @NotNull String KEY =
      "map_id = ? and rotation = ? and zoom = ? and x = ? and z = ? and layer = ?";

  private final @NotNull Logger logger;
  private final @NotNull HikariDataSource dataSource;
  private final @NotNull QueryConfiguration queries;
  private final @NotNull Map<MapKey, UUID> maps = new ConcurrentHashMap<>();
  private volatile boolean closing;

  private SqlTileStore(final @NotNull HikariDataSource dataSource, final @NotNull Logger logger)
      throws IOException {
    super();
    this.logger = logger;
    this.dataSource = dataSource;
    try {
      SqlUpdater.builder(dataSource, MariaDb.get())
          .setVersionTable("taerra_database_version")
          .withClassLoader(SqlTileStore.class.getClassLoader())
          .execute();
    } catch (final SQLException e) {
      throw new IOException("The database schema update failed", e);
    }
    this.queries =
        QueryConfiguration.builder(dataSource)
            .setRowMapperRegistry(
                new RowMapperRegistry().register(MariaDbMapper.getDefaultMapper()))
            .setThrowExceptions(true)
            .setExceptionHandler(_ -> {})
            .build();
    for (final @NotNull MapEntry entry :
        run(
            () ->
                this.queries
                    .query("select map_id, world, view from taerra_maps")
                    .single()
                    .map(
                        row ->
                            new MapEntry(
                                uuid(row.getBytes("map_id")),
                                row.getString("world"),
                                row.getString("view")))
                    .all())) {
      this.maps.put(
          new MapKey(entry.world(), MapView.fromId(entry.view(), MapView.TOP_DOWN)), entry.id());
    }
    Thread.ofPlatform().name("Taerra-Delete").daemon().start(this::deleteOrphans);
  }

  static @NotNull SqlTileStore open(
      final @NotNull StorageSettings settings, final @NotNull Logger logger) throws IOException {
    final @NotNull HikariDataSource dataSource;
    try {
      dataSource =
          DataSourceCreator.create(MariaDb.get())
              .configure(
                  config ->
                      config
                          .host(settings.host())
                          .port(settings.port())
                          .database(settings.name())
                          .user(settings.user())
                          .password(settings.password())
                          .connectTimeout((int) CONNECT_MILLIS))
              .create()
              .withPoolName("taerra-tiles")
              .withConnectionTimeout(CONNECT_MILLIS)
              .build();
    } catch (final RuntimeException e) {
      throw new IOException(Throwables.describe(e), e);
    }
    try {
      return new SqlTileStore(dataSource, logger);
    } catch (final IOException | RuntimeException e) {
      dataSource.close();
      throw e;
    }
  }

  private static <T> T run(final @NotNull Supplier<T> query) throws IOException {
    try {
      return query.get();
    } catch (final RuntimeException e) {
      throw new IOException("Database query failed: " + e.getMessage(), e);
    }
  }

  private static byte @NotNull [] bytes(final @NotNull UUID id) {
    return ByteBuffer.allocate(16)
        .putLong(id.getMostSignificantBits())
        .putLong(id.getLeastSignificantBits())
        .array();
  }

  private static @NotNull UUID uuid(final byte @NotNull [] bytes) {
    final @NotNull ByteBuffer buffer = ByteBuffer.wrap(bytes);
    return new UUID(buffer.getLong(), buffer.getLong());
  }

  private static @NotNull Call key(
      final @NotNull UUID map, final @NotNull TileCoordinate coord, final @NotNull FogView layer) {
    return Call.of()
        .bind(bytes(map))
        .bind(coord.rotation().index())
        .bind(coord.zoom())
        .bind(coord.x())
        .bind(coord.z())
        .bind(layer.id());
  }

  private @Nullable UUID map(final @NotNull String world, final @NotNull MapView view) {
    return this.maps.get(new MapKey(world, view));
  }

  private @NotNull UUID createMap(final @NotNull String world, final @NotNull MapView view)
      throws IOException {
    final @NotNull MapKey key = new MapKey(world, view);
    synchronized (this.maps) {
      final @Nullable UUID known = this.maps.get(key);
      if (known != null) {
        return known;
      }
      final @NotNull UUID id =
          run(
              () -> {
                this.queries
                    .query("insert ignore into taerra_maps (map_id, world, view) values (?, ?, ?)")
                    .single(Call.of().bind(bytes(UUID.randomUUID())).bind(world).bind(view.id()))
                    .insert();
                return this.queries
                    .query("select map_id from taerra_maps where world = ? and view = ?")
                    .single(Call.of().bind(world).bind(view.id()))
                    .map(row -> uuid(row.getBytes("map_id")))
                    .first()
                    .orElseThrow();
              });
      this.maps.put(key, id);
      return id;
    }
  }

  @Override
  public byte @Nullable [] read(
      final @NotNull TileCoordinate coord, final @NotNull FogView layer, final @NotNull Kind kind)
      throws IOException {
    final @Nullable UUID map = map(coord.world(), coord.view());
    return map == null
        ? null
        : run(
            () ->
                this.queries
                    .query("select data from taerra_tiles where " + KEY + " and kind = ?")
                    .single(key(map, coord, layer).bind(kind.ordinal()))
                    .map(row -> row.getBytes("data"))
                    .first()
                    .orElse(null));
  }

  @Override
  public void write(
      final @NotNull TileCoordinate coord,
      final @NotNull FogView layer,
      final @NotNull Kind kind,
      final byte @NotNull [] data)
      throws IOException {
    final @NotNull UUID map = createMap(coord.world(), coord.view());
    run(
        () ->
            this.queries
                .query(
                    """
                    insert into taerra_tiles (map_id, rotation, zoom, x, z, layer, kind, data, modified)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    on duplicate key update data = values(data), modified = values(modified)
                    """)
                .single(
                    key(map, coord, layer)
                        .bind(kind.ordinal())
                        .bind(data)
                        .bind(System.currentTimeMillis()))
                .insert());
  }

  @Override
  public boolean writeIfNewer(
      final @NotNull TileCoordinate coord,
      final @NotNull FogView layer,
      final @NotNull Kind kind,
      final byte @NotNull [] data,
      final long modified)
      throws IOException {
    final @NotNull UUID map = createMap(coord.world(), coord.view());
    return run(
        () ->
            this.queries
                .query(
                    """
                    insert into taerra_tiles (map_id, rotation, zoom, x, z, layer, kind, data, modified)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    on duplicate key update
                      data = if(modified < values(modified), values(data), data),
                      modified = greatest(modified, values(modified))
                    """)
                .single(key(map, coord, layer).bind(kind.ordinal()).bind(data).bind(modified))
                .insert()
                .changed());
  }

  @Override
  public void forEach(final @NotNull Set<String> worlds, final @NotNull Visitor visitor)
      throws IOException {
    for (final Map.@NotNull Entry<MapKey, UUID> map : Map.copyOf(this.maps).entrySet()) {
      if (!worlds.contains(map.getKey().world())) {
        continue;
      }
      final byte @NotNull [] id = bytes(map.getValue());
      final @NotNull List<Column> columns =
          run(
              () ->
                  this.queries
                      .query("select distinct rotation, zoom, x from taerra_tiles where map_id = ?")
                      .single(Call.of().bind(id))
                      .map(
                          row ->
                              new Column(
                                  row.getInt("rotation"), row.getInt("zoom"), row.getInt("x")))
                      .all());
      for (final @NotNull Column column : columns) {
        @Nullable StoredTile last = null;
        while (true) {
          final @Nullable StoredTile after = last;
          final @NotNull List<StoredTile> page =
              run(
                  () ->
                      this.queries
                          .query(
                              """
                              select z, layer, kind, data, modified from taerra_tiles
                              where map_id = ? and rotation = ? and zoom = ? and x = ?
                                and (z, layer, kind) > (?, ?, ?)
                              order by z, layer, kind
                              limit %d
                              """
                                  .formatted(COPY_BATCH))
                          .single(
                              Call.of()
                                  .bind(id)
                                  .bind(column.rotation())
                                  .bind(column.zoom())
                                  .bind(column.x())
                                  .bind(after == null ? Integer.MIN_VALUE : after.z())
                                  .bind(after == null ? "" : after.layer())
                                  .bind(after == null ? -1 : after.kind()))
                          .map(
                              row ->
                                  new StoredTile(
                                      row.getInt("z"),
                                      row.getString("layer"),
                                      row.getInt("kind"),
                                      row.getBytes("data"),
                                      row.getLong("modified")))
                          .all());
          for (final @NotNull StoredTile tile : page) {
            final @Nullable FogView layer = FogView.parse(tile.layer());
            if (layer != null && tile.kind() >= 0 && tile.kind() < Kind.values().length) {
              visitor.visit(
                  new TileCoordinate(
                      map.getKey().world(),
                      map.getKey().view(),
                      MapRotation.fromIndex(column.rotation(), MapRotation.R0),
                      column.zoom(),
                      column.x(),
                      tile.z()),
                  layer,
                  Kind.values()[tile.kind()],
                  tile.data(),
                  tile.modified());
            }
          }
          if (page.size() < COPY_BATCH) {
            break;
          }
          last = page.getLast();
        }
      }
    }
  }

  @Override
  public long count(final @NotNull Set<String> worlds) throws IOException {
    long count = 0;
    for (final Map.@NotNull Entry<MapKey, UUID> map : Map.copyOf(this.maps).entrySet()) {
      if (worlds.contains(map.getKey().world())) {
        count +=
            run(
                () ->
                    this.queries
                        .query("select count(*) as count from taerra_tiles where map_id = ?")
                        .single(Call.of().bind(bytes(map.getValue())))
                        .map(row -> row.getLong("count"))
                        .first()
                        .orElse(0L));
      }
    }
    return count;
  }

  @Override
  public void touch(final @NotNull TileCoordinate coord, final @NotNull FogView layer)
      throws IOException {
    final @Nullable UUID map = map(coord.world(), coord.view());
    if (map == null
        || !run(
            () ->
                this.queries
                    .query("update taerra_tiles set modified = ? where " + KEY + " and kind = ?")
                    .single(
                        Call.of()
                            .bind(System.currentTimeMillis())
                            .bind(bytes(map))
                            .bind(coord.rotation().index())
                            .bind(coord.zoom())
                            .bind(coord.x())
                            .bind(coord.z())
                            .bind(layer.id())
                            .bind(Kind.TILE.ordinal()))
                    .update()
                    .changed())) {
      throw new IOException("No tile at " + coord);
    }
  }

  @Override
  public boolean delete(final @NotNull TileCoordinate coord, final @NotNull FogView layer)
      throws IOException {
    final @Nullable UUID map = map(coord.world(), coord.view());
    return map != null
        && run(
            () ->
                this.queries
                    .query("delete from taerra_tiles where " + KEY)
                    .single(key(map, coord, layer))
                    .delete()
                    .changed());
  }

  @Override
  public long lastModified(final @NotNull TileCoordinate coord) throws IOException {
    final @Nullable UUID map = map(coord.world(), coord.view());
    return map == null
        ? 0
        : run(
            () ->
                this.queries
                    .query("select modified from taerra_tiles where " + KEY + " and kind = ?")
                    .single(key(map, coord, FogView.BASE).bind(Kind.TILE.ordinal()))
                    .map(row -> row.getLong("modified"))
                    .first()
                    .orElse(0L));
  }

  @Override
  public void deleteView(final @NotNull String worldName, final @NotNull MapView view)
      throws IOException {
    final @Nullable UUID map;
    synchronized (this.maps) {
      map = this.maps.remove(new MapKey(worldName, view));
    }
    if (map == null) {
      return;
    }
    run(
        () ->
            this.queries
                .query("delete from taerra_maps where map_id = ?")
                .single(Call.of().bind(bytes(map)))
                .delete());
    Thread.ofPlatform().name("Taerra-Delete").daemon().start(() -> deleteTiles(map));
  }

  private void deleteOrphans() {
    try {
      for (final byte @NotNull [] map :
          run(
              () ->
                  this.queries
                      .query(
                          """
                          select distinct t.map_id from taerra_tiles t
                          left join taerra_maps m on m.map_id = t.map_id
                          where m.map_id is null
                          """)
                      .single()
                      .map(row -> row.getBytes("map_id"))
                      .all())) {
        deleteTiles(uuid(map));
      }
    } catch (final IOException e) {
      warnUnlessClosed("Failed to look for deleted tiles: " + e.getMessage());
    }
  }

  private void deleteTiles(final @NotNull UUID map) {
    try {
      while (!this.closing
          && run(
              () ->
                  this.queries
                      .query("delete from taerra_tiles where map_id = ? limit " + DELETE_BATCH)
                      .single(Call.of().bind(bytes(map)))
                      .delete()
                      .changed())) {}
    } catch (final IOException e) {
      warnUnlessClosed("Failed to delete old tiles: " + e.getMessage());
    }
  }

  private void warnUnlessClosed(final @NotNull String message) {
    if (!this.closing) {
      this.logger.warning(message);
    }
  }

  @Override
  public void deleteLayers(final @NotNull String worldName) throws IOException {
    for (final @NotNull MapView view : MapView.values()) {
      final @Nullable UUID map = map(worldName, view);
      if (map != null) {
        run(
            () ->
                this.queries
                    .query("delete from taerra_tiles where map_id = ? and layer <> ''")
                    .single(Call.of().bind(bytes(map)))
                    .delete());
      }
    }
  }

  @Override
  public @NotNull Map<Integer, Set<FogView>> layers(final @NotNull TileCoordinate column)
      throws IOException {
    final @NotNull Map<Integer, Set<FogView>> found = new ConcurrentHashMap<>();
    final @Nullable UUID map = map(column.world(), column.view());
    if (map == null) {
      return found;
    }
    for (final @NotNull StoredLayer stored :
        run(
            () ->
                this.queries
                    .query(
                        """
                        select z, layer from taerra_tiles
                        where map_id = ? and rotation = ? and zoom = ? and x = ?
                          and layer <> '' and kind = ?
                        """)
                    .single(
                        Call.of()
                            .bind(bytes(map))
                            .bind(column.rotation().index())
                            .bind(column.zoom())
                            .bind(column.x())
                            .bind(Kind.TILE.ordinal()))
                    .map(
                        row ->
                            new StoredLayer(row.getInt("z"), FogView.parse(row.getString("layer"))))
                    .all())) {
      if (stored.layer() != null && !stored.layer().base()) {
        found.computeIfAbsent(stored.z(), _ -> ConcurrentHashMap.newKeySet()).add(stored.layer());
      }
    }
    return found;
  }

  @Override
  public @NotNull List<TileCoordinate> tilesAt(final @NotNull TileCoordinate level)
      throws IOException {
    final @Nullable UUID map = map(level.world(), level.view());
    return map == null
        ? List.of()
        : run(
            () ->
                this.queries
                    .query(
                        """
                        select x, z from taerra_tiles
                        where map_id = ? and rotation = ? and zoom = ? and layer = '' and kind = ?
                        """)
                    .single(
                        Call.of()
                            .bind(bytes(map))
                            .bind(level.rotation().index())
                            .bind(level.zoom())
                            .bind(Kind.TILE.ordinal()))
                    .map(row -> level.at(level.zoom(), row.getInt("x"), row.getInt("z")))
                    .all());
  }

  @Override
  public @NotNull List<TileCoordinate> tilesOlderThan(
      final @NotNull String worldName, final long cutoffMillis) throws IOException {
    final @NotNull List<TileCoordinate> stale = new ArrayList<>();
    for (final @NotNull MapView view : MapView.values()) {
      final @Nullable UUID map = map(worldName, view);
      if (map == null) {
        continue;
      }
      stale.addAll(
          run(
              () ->
                  this.queries
                      .query(
                          """
                          select rotation, zoom, x, z from taerra_tiles
                          where map_id = ? and layer = '' and kind = ? and modified < ?
                          """)
                      .single(
                          Call.of().bind(bytes(map)).bind(Kind.TILE.ordinal()).bind(cutoffMillis))
                      .map(
                          row ->
                              new TileCoordinate(
                                  worldName,
                                  view,
                                  MapRotation.fromIndex(row.getInt("rotation"), MapRotation.R0),
                                  row.getInt("zoom"),
                                  row.getInt("x"),
                                  row.getInt("z")))
                      .all()));
    }
    return stale;
  }

  @Override
  public void close() {
    this.closing = true;
    final @Nullable HikariPoolMXBean pool = this.dataSource.getHikariPoolMXBean();
    for (int i = 0; i < CLOSE_WAITS && pool != null && pool.getActiveConnections() > 0; i++) {
      try {
        Thread.sleep(100);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    this.dataSource.close();
  }

  private record MapKey(@NotNull String world, @NotNull MapView view) {}

  private record MapEntry(@NotNull UUID id, @NotNull String world, @NotNull String view) {}

  private record Column(int rotation, int zoom, int x) {}

  private record StoredTile(
      int z, @NotNull String layer, int kind, byte @NotNull [] data, long modified) {}

  private record StoredLayer(int z, @Nullable FogView layer) {}
}
