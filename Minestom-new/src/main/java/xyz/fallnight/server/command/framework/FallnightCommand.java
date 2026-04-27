package xyz.fallnight.server.command.framework;

import java.util.List;
import java.util.Arrays;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.condition.CommandCondition;
import net.minestom.server.entity.Player;

public abstract class FallnightCommand extends Command {
    protected FallnightCommand(String name, PermissionService permissionService, String... aliases) {
        super(name, aliases);
        setCondition(permissionCondition(permissionService));
    }

    public abstract String permission();

    public abstract String summary();

    public abstract String usage();

    protected boolean ensurePlayer(net.minestom.server.command.CommandSender sender) {
        if (sender instanceof Player) {
            return true;
        }

        sender.sendMessage(CommandMessages.error("Sender needs to be a player."));
        return false;
    }

    protected void sendUsage(net.minestom.server.command.CommandSender sender) {
        sender.sendMessage(CommandMessages.info("Usage: " + usage()));
    }

    public List<String> allNames() {
        return Arrays.asList(getNames());
    }

    private CommandCondition permissionCondition(PermissionService permissionService) {
        return (sender, commandString) -> {
            if (permissionService.hasPermission(sender, permission())) {
                return true;
            }

            if (commandString != null) {
                sender.sendMessage(CommandMessages.error("You do not have permission (" + permission() + ")."));
            }
            return false;
        };
    }
}
