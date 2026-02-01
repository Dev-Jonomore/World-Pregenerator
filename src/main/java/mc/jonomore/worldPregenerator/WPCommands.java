package mc.jonomore.worldPregenerator;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.util.Tick;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;

public class WPCommands {
  private static BukkitTask reset_timer = null;

  public static LiteralCommandNode<CommandSourceStack> createCommand(WorldPregenerator wp) {
    return Commands.literal("worldpregenerator")
        .then(Commands.literal("start")
            .executes(ctx -> {
              wp.start();
              return Command.SINGLE_SUCCESS;
            })
        )
        .then(Commands.literal("stop")
            .executes(ctx -> {
              wp.stop();
              return Command.SINGLE_SUCCESS;
            })
        )
        .then(Commands.literal("reset")
            .executes(ctx -> {
              // send warning message
              ctx.getSource().getSender().sendMessage(
                  MiniMessage.miniMessage().deserialize(
                      "<gray>[<yellow>Hey!<gray>] " +
                          "<red>You're about to delete all your worlds!<newline>" +
                          "<red>Click <click:run_command:/worldpregenerator confirm>confirm</click> " +
                          "in the next 30 seconds to continue."
                  )
              );

              if (reset_timer != null) reset_timer.cancel();
              reset_timer = Bukkit.getScheduler().runTaskLater(
                  wp,
                  () -> reset_timer = null,
                  Tick.tick().fromDuration(java.time.Duration.ofSeconds(30L))
              );

              return Command.SINGLE_SUCCESS;
            })
        )
        .then(Commands.literal("confirm")
            .executes(ctx -> hardReset(ctx, wp))
        )
        .then(Commands.literal("reload")
            .executes(ctx -> {
              wp.config.loadConfig();
              return Command.SINGLE_SUCCESS;
            })
        )
        .then(Commands.literal("info")
            .executes(ctx -> {
              for (String line : wp.config.toString().split("\n")) {
                wp.getLogger().info(line);
              }
              return Command.SINGLE_SUCCESS;
            })
        )
        .then(Commands.literal("config")
            .then(Commands.argument("setting", StringArgumentType.word())
                .then(Commands.argument("value", StringArgumentType.word())
                    .executes(ctx -> {
                      String setting = StringArgumentType.getString(ctx, "setting");
                      String value = StringArgumentType.getString(ctx, "value");
                      wp.getConfig().set(setting, value);
                      wp.saveConfig();
                      wp.config.loadConfig();
                      return Command.SINGLE_SUCCESS;
                    })
                )
            )
        ).build();
  }

  private static int hardReset(CommandContext<CommandSourceStack> ctx, WorldPregenerator wp) {
    if (reset_timer == null || reset_timer.isCancelled()) {
      ctx.getSource().getSender().sendMessage(
          MiniMessage.miniMessage().deserialize(
              "Reset window has expired or was never opened!"
          )
      );
      return Command.SINGLE_SUCCESS;
    }
    reset_timer.cancel();

    wp.stop();

    File world_dir = new File(wp.config.getExportPath());
    File[] worlds = world_dir.listFiles();

    if (worlds == null) {
      ctx.getSource().getSender().sendMessage(
          MiniMessage.miniMessage().deserialize(
              "<yellow>[WARNING] Failed to extract worlds from world directory."
          )
      );
    } else if (worlds.length > 0) {
      ctx.getSource().getSender().sendMessage("Deleting " + worlds.length + " exported worlds...");
      Bukkit.getScheduler().runTaskAsynchronously(wp, () -> {
        for (File world_folder : worlds) {
          try {
            util.deleteDirectory(world_folder);
          } catch (IOException e) {
            Bukkit.getScheduler().runTask(wp, () ->
                ctx.getSource().getSender().sendMessage(
                    MiniMessage.miniMessage().deserialize(
                        "<red>[ERROR] Failed to delete world folder. " + e.getMessage()
                    )
                )
            );
          }
        }

        Bukkit.getScheduler().runTask(wp, () -> {
          File[] remaining_worlds = world_dir.listFiles();
          if (remaining_worlds != null && remaining_worlds.length != 0) {
            ctx.getSource().getSender().sendMessage(
                MiniMessage.miniMessage().deserialize(
                    "<yellow>[WARNING] there are still <dark_red>" +
                        remaining_worlds.length + "</dark_red> worlds in your world directory."
                )
            );
          } else {
            ctx.getSource().getSender().sendMessage(
                MiniMessage.miniMessage().deserialize(
                    "<green>All exported worlds successfully deleted!"
                )
            );
          }
        });
      });
    } else {
      ctx.getSource().getSender().sendMessage("No exported worlds to delete!");
    }

    // Reset the generation task, which also deletes any partially generated world
    if (wp.task != null) {
      wp.task.reset();
      wp.task = null;
      ctx.getSource().getSender().sendMessage("Generation task has been reset.");
    }

    return Command.SINGLE_SUCCESS;
  }
}
