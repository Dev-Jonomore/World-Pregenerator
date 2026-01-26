package mc.jonomore.worldPregenerator;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

public class WPCommands {

  public static LiteralCommandNode<CommandSourceStack> createCommand() {
    return Commands.literal("worldpregenerator")
        .then(Commands.literal("start"))
        .then(Commands.literal("stop"))
        .then(Commands.literal("reset"))
        .then(Commands.literal("confirm"))
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
