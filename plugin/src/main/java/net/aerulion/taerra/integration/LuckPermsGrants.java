package net.aerulion.taerra.integration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.stream.Collectors;
import net.aerulion.taerra.config.TaerraConfig;
import net.aerulion.taerra.web.auth.WebGrants;
import net.aerulion.taerra.web.auth.WebSessions;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.node.NodeMutateEvent;
import net.luckperms.api.event.sync.PostNetworkSyncEvent;
import net.luckperms.api.event.sync.PostSyncEvent;
import net.luckperms.api.event.sync.SyncType;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.OptionKey;
import net.luckperms.api.util.Tristate;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LuckPermsGrants {

  private static final long REFRESH_TICKS = 20L;
  private static final @NotNull OptionKey<Boolean> OP = OptionKey.of("op", Boolean.class);

  private final @NotNull Plugin plugin;
  private final @NotNull TaerraConfig config;
  private final @NotNull WebSessions sessions;
  private final @NotNull LuckPerms luckPerms;
  private final @NotNull Set<UUID> changed = ConcurrentHashMap.newKeySet();
  private final @NotNull AtomicBoolean everyone = new AtomicBoolean(true);

  private LuckPermsGrants(
      final @NotNull Plugin plugin,
      final @NotNull TaerraConfig config,
      final @NotNull WebSessions sessions,
      final @NotNull LuckPerms luckPerms) {
    super();
    this.plugin = plugin;
    this.config = config;
    this.sessions = sessions;
    this.luckPerms = luckPerms;
  }

  public static void start(
      final @NotNull Plugin plugin,
      final @NotNull TaerraConfig config,
      final @NotNull WebSessions sessions) {
    final @NotNull LuckPerms luckPerms = LuckPermsProvider.get();
    final @NotNull LuckPermsGrants grants =
        new LuckPermsGrants(plugin, config, sessions, luckPerms);
    final @NotNull EventBus events = luckPerms.getEventBus();
    events.subscribe(plugin, NodeMutateEvent.class, grants::onNodes);
    events.subscribe(plugin, PostSyncEvent.class, _ -> grants.everyone.set(true));
    events.subscribe(plugin, PostNetworkSyncEvent.class, grants::onNetworkSync);
    Bukkit.getScheduler().runTaskTimer(plugin, grants::refresh, REFRESH_TICKS, REFRESH_TICKS);
  }

  private static boolean has(
      final @NotNull CachedPermissionData data,
      final @NotNull OfflinePlayer player,
      final @NotNull String node) {
    final @NotNull Tristate value = data.checkPermission(node);
    if (value != Tristate.UNDEFINED) {
      return value.asBoolean();
    }
    final @Nullable Permission permission = Bukkit.getPluginManager().getPermission(node);
    return (permission == null ? Permission.DEFAULT_PERMISSION : permission.getDefault())
        .getValue(player.isOp());
  }

  private void onNodes(final @NotNull NodeMutateEvent event) {
    if (event.getTarget() instanceof final User user) {
      this.changed.add(user.getUniqueId());
    } else {
      this.everyone.set(true);
    }
  }

  private void onNetworkSync(final @NotNull PostNetworkSyncEvent event) {
    if (!event.didSyncOccur()) {
      return;
    }
    final @Nullable UUID user = event.getSpecificUserUniqueId();
    if (event.getType() == SyncType.SPECIFIC_USER && user != null) {
      this.changed.add(user);
    } else {
      this.everyone.set(true);
    }
  }

  private void refresh() {
    if (this.changed.isEmpty() && !this.everyone.get()) {
      return;
    }
    final @NotNull Set<UUID> due = new HashSet<>(this.changed);
    this.changed.removeAll(due);
    final @NotNull Set<UUID> accounts = this.sessions.accounts();
    if (this.everyone.getAndSet(false)) {
      due.addAll(accounts);
    }
    due.retainAll(accounts);
    due.removeIf(uuid -> Bukkit.getPlayer(uuid) != null);
    if (due.isEmpty()) {
      return;
    }
    final @NotNull List<CompletableFuture<User>> loads =
        due.stream().map(this.luckPerms.getUserManager()::loadUser).toList();
    CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new))
        .whenComplete(
            (_, error) -> {
              if (error != null) {
                this.plugin
                    .getLogger()
                    .log(Level.WARNING, "Failed to load permissions from LuckPerms", error);
              }
              if (this.plugin.isEnabled()) {
                Bukkit.getScheduler()
                    .runTask(
                        this.plugin,
                        () ->
                            grant(
                                loads.stream()
                                    .filter(load -> !load.isCompletedExceptionally())
                                    .map(CompletableFuture::join)
                                    .toList()));
              }
            });
  }

  private void grant(final @NotNull List<User> users) {
    final @NotNull Set<UUID> accounts = this.sessions.accounts();
    final @NotNull Map<UUID, Set<String>> granted = new HashMap<>();
    for (final @NotNull User user : users) {
      final @NotNull UUID uuid = user.getUniqueId();
      if (Bukkit.getPlayer(uuid) == null && accounts.contains(uuid)) {
        granted.put(uuid, grants(user));
      }
    }
    this.sessions.grant(granted);
  }

  private @NotNull Set<String> grants(final @NotNull User user) {
    final @NotNull OfflinePlayer player = Bukkit.getOfflinePlayer(user.getUniqueId());
    final @NotNull CachedPermissionData data =
        user.getCachedData()
            .getPermissionData(
                this.luckPerms.getContextManager().getStaticQueryOptions().toBuilder()
                    .option(OP, player.isOp())
                    .build());
    return WebGrants.nodes(this.config)
        .filter(node -> has(data, player, node))
        .collect(Collectors.toUnmodifiableSet());
  }
}
