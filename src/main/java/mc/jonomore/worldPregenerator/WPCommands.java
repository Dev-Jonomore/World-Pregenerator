package mc.jonomore.worldPregenerator;

import com.mojang.brigadier.arguments.LongArgumentType;
import mc.jonomore.worldPregenerator.config.ConfigValidator;
import mc.jonomore.worldPregenerator.generation.GenerationState;
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

import java.util.stream.Collectors;

public class WPCommands {
  private static BukkitTask reset_timer = null;

  public static LiteralCommandNode<CommandSourceStack> createCommand(WorldPregenerator wp) {
    return Commands.literal("worldpregenerator")
        .executes(ctx -> {
            sendHelp(ctx.getSource().getSender());
            return Command.SINGLE_SUCCESS;
        })
        .then(Commands.literal("help")
            .executes(ctx -> {
                sendHelp(ctx.getSource().getSender());
                return Command.SINGLE_SUCCESS;
            })
        )
        .then(Commands.literal("start")
            .executes(_ -> {
              wp.start();
              return Command.SINGLE_SUCCESS;
            })
        )
        .then(Commands.literal("dryrun")
            .executes(ctx -> {
                ConfigValidator validator = new ConfigValidator(wp);
                for (String result : validator.validate()) {
                    ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize(result));
                }
                return Command.SINGLE_SUCCESS;
            })
        )
        .then(Commands.literal("test-one")
            .executes(_ -> {
              wp.testOne(null);
              return Command.SINGLE_SUCCESS;
            })
            .then(Commands.argument("seed", LongArgumentType.longArg())
                .executes(ctx -> {
                  wp.testOne(LongArgumentType.getLong(ctx, "seed"));
                  return Command.SINGLE_SUCCESS;
                })
            )
        )
        .then(Commands.literal("stop")
            .executes(_ -> {
              wp.stop();
              return Command.SINGLE_SUCCESS;
            })
        )
        .then(Commands.literal("retry")
            .executes(_ -> {
                wp.retry();
                return Command.SINGLE_SUCCESS;
            })
        )
        .then(Commands.literal("status")
            .executes(ctx -> {
                GenerationState state = wp.state;
                if (state == null) {
                    ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize("<red>No generation state found. Start one with /wp start"));
                    return Command.SINGLE_SUCCESS;
                }

                String progress = String.format("%.2f%%", state.getProgress() * 100);
                long etrMillis = state.estimateTimeRemaining();
                String etr = etrMillis < 0 ? "Calculating..." : formatTime(etrMillis);

                ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize(
                    "<gold>=== WorldPregenerator Status ===<newline>" +
                    "<gray>Step: <white>" + state.getCurrentStep() + "<newline>" +
                    "<gray>Progress: <white>" + state.getCurrentIndex() + "/" + state.getTotalSeeds() + " (" + progress + ")<newline>" +
                    "<gray>Success: <green>" + state.getSuccessCount() + "<newline>" +
                    "<gray>Failure: <red>" + state.getFailureCount() + "<newline>" +
                    "<gray>ETR: <white>" + etr + "<newline>" +
                    "<gray>Current World: <white>" + (state.getCurrentWorldName() == null ? "None" : state.getCurrentWorldName())
                ));
                return Command.SINGLE_SUCCESS;
            })
        )
        .then(Commands.literal("failed")
            .executes(ctx -> {
                GenerationState state = wp.state;
                if (state == null || state.getFailedSeeds().isEmpty()) {
                    ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize("<green>No failed seeds found."));
                    return Command.SINGLE_SUCCESS;
                }

                String failedList = state.getFailedSeeds().stream()
                    .map(entry -> entry.seedEntry().seed() + " (" + entry.reason() + ")")
                    .collect(Collectors.joining("<newline>"));
                
                ctx.getSource().getSender().sendMessage(MiniMessage.miniMessage().deserialize(
                    "<red>Failed Seeds (" + state.getFailedSeeds().size() + "):<newline><gray>" + failedList
                ));
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
            .executes(ctx -> handleReset(ctx, wp))
        )
        .then(Commands.literal("reload")
            .executes(_ -> {
              wp.config.loadConfig();
              return Command.SINGLE_SUCCESS;
            })
        )
        .then(Commands.literal("info")
            .executes(_ -> {
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
                      try {
                        wp.config.set(setting, value);
                      } catch (java.io.IOException e) {
                        wp.getLogger().log(java.util.logging.Level.SEVERE, "Failed to update config.yml", e);
                        return 0;
                      }
                      return Command.SINGLE_SUCCESS;
                    })
                )
            )
        ).build();
  }

  private static void sendHelp(org.bukkit.command.CommandSender sender) {
    sender.sendMessage(MiniMessage.miniMessage().deserialize(
        "<gold>=== WorldPregenerator Help ===<newline>" +
        "<yellow>/wp start <gray>- Start or resume generation<newline>" +
        "<yellow>/wp stop <gray>- Stop current generation<newline>" +
        "<yellow>/wp status <gray>- Show current progress<newline>" +
        "<yellow>/wp failed <gray>- Show seeds that failed with reasons<newline>" +
        "<yellow>/wp retry <gray>- Retry only failed seeds<newline>" +
        "<yellow>/wp dryrun <gray>- Validate config without generating<newline>" +
        "<yellow>/wp reload <gray>- Reload configuration from disk<newline>" +
        "<yellow>/wp info <gray>- Show current config values<newline>" +
        "<yellow>/wp reset <gray>- Delete all exported worlds and reset progress<newline>" +
        "<yellow>/wp config <setting> <value> <gray>- Change a config value<newline>" +
        "<yellow>/wp help <gray>- Show this help message"
    ));
  }

  private static String formatTime(long millis) {
      long seconds = millis / 1000;
      long minutes = seconds / 60;
      long hours = minutes / 60;
      return String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60);
  }

  private static int handleReset(CommandContext<CommandSourceStack> ctx, WorldPregenerator wp) {
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

    // Reset the generation task, which also deletes any partially generated world
    wp.reset();
    ctx.getSource().getSender().sendMessage("Generation task has been reset.");

    return Command.SINGLE_SUCCESS;
  }
}