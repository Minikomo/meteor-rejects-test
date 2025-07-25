package anticope.rejects.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;

public class ReconnectCommand extends Command {
    public ReconnectCommand() {
        super("reconnect", "Reconnects server.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            ServerInfo info = mc.isInSingleplayer() ? null : mc.getCurrentServerEntry();
            if (info != null) {
                mc.world.disconnect(Text.literal(""));
                ConnectScreen.connect(new MultiplayerScreen(new TitleScreen()), mc,
                        ServerAddress.parse(info.address), info, false, null);
            }
            return SINGLE_SUCCESS;
        });
    }
}
