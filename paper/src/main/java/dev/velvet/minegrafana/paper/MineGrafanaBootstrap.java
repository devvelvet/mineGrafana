package dev.velvet.minegrafana.paper;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;

/**
 * Paper PluginBootstrap — runs at a very early stage.
 * Written in Java because Kotlin runtime may not be available yet.
 */
public class MineGrafanaBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {
        int javaVersion = Runtime.version().feature();
        if (javaVersion < 21) {
            context.getLogger().error("mineGrafana requires Java 21+. Current: " + javaVersion);
            return;
        }
        context.getLogger().info("mineGrafana bootstrap complete. Java " + javaVersion + " detected.");
    }
}
