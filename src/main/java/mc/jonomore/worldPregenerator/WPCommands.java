package mc.jonomore.worldPregenerator;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.util.Tick;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;

public class WPCommands {
  private static boolean reset_window = false;

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

              // open reset window
              reset_window = true;

              // set a 30-second timer before reset window expires
              Bukkit.getScheduler().runTaskLater(
                  wp,
                  () -> reset_window = false,
                  Tick.tick().fromDuration(java.time.Duration.ofSeconds(30L))
              );

              return Command.SINGLE_SUCCESS;
            })
        )
        .then(Commands.literal("confirm")
            .executes(ctx -> {
              if (reset_window) {
                wp.stop();
                // TODO: delete directories in Worlds folder and reset the task
              } else {
                ctx.getSource().getSender().sendMessage(
                    MiniMessage.miniMessage().deserialize(
                        "Reset window has expired or was never opened!"
                    )
                );
              }
              return Command.SINGLE_SUCCESS;
            }))
        .then(Commands.literal("status"))
        .then(Commands.literal("reload"))
        .then(Commands.literal("info"))
        .then(Commands.literal("config")
            .then(Commands.argument("setting", StringArgumentType.word())
                .then(Commands.argument("value", StringArgumentType.word()))
            )
        ).build();
  }
}
