package net.aerulion.taerra.integration;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
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
    final @NotNull Set<UUID> due = new HashSet<>(this.changed);
    this.changed.removeAll(due);
    final @NotNull Set<UUID> accounts = this.sessions.accounts();
    if (this.everyone.getAndSet(false)) {
      due.addAll(accounts);
    }
    due.retainAll(accounts);
    for (final @NotNull UUID uuid : due) {
      if (Bukkit.getPlayer(uuid) == null) {
        this.luckPerms
            .getUserManager()
            .loadUser(uuid)
            .whenComplete(
                (user, error) -> {
                  if (error != null) {
                    this.plugin
                        .getLogger()
                        .log(Level.WARNING, "Failed to load the permissions of " + uuid, error);
                  } else if (this.plugin.isEnabled()) {
                    Bukkit.getScheduler().runTask(this.plugin, () -> grant(user));
                  }
                });
      }
    }
  }

  private void grant(final @NotNull User user) {
    final @NotNull UUID uuid = user.getUniqueId();
    if (Bukkit.getPlayer(uuid) != null || !this.sessions.accounts().contains(uuid)) {
      return;
    }
    final @NotNull OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
    final @NotNull CachedPermissionData data =
        user.getCachedData()
            .getPermissionData(
                this.luckPerms.getContextManager().getStaticQueryOptions().toBuilder()
                    .option(OP, player.isOp())
                    .build());
    this.sessions.grant(
        uuid,
        WebGrants.nodes(this.config)
            .filter(node -> has(data, player, node))
            .collect(Collectors.toUnmodifiableSet()));
  }
}
