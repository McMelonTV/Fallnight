package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import xyz.fallnight.server.service.VotePartyService;

public final class VoteCommand extends FallnightCommand {
    private final VotePartyService votePartyService;

    public VoteCommand(PermissionService permissionService, VotePartyService votePartyService) {
        super("vote", permissionService);
        this.votePartyService = votePartyService;

        setDefaultExecutor((sender, context) -> sendStatus(sender));
    }

    @Override
    public String permission() {
        return "fallnight.command.vote";
    }

    @Override
    public String summary() {
        return "vote for the server";
    }

    @Override
    public String usage() {
        return "/vote";
    }

    private void sendStatus(net.minestom.server.command.CommandSender sender) {
        sender.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(
                "§8§l<--§bFN§8-->§r"
                        + "\n§b Fallnight§r§7 voting§r"
//                + "\n§b > §r§7Vote link: §bvote.fallnight.xyz§r"
//                + "\n§b §7Once voted, your rewards will be given automatically."
                        + "\n§b §7voting is not set up"
                        + "\n§r§8§l<--++-->⛏"
        ));
    }
}
