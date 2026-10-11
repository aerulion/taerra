package net.aerulion.taerra.web.http.route;

import io.javalin.config.RoutesConfig;
import org.jetbrains.annotations.NotNull;

public interface Routes {

  void register(@NotNull RoutesConfig routes);
}
