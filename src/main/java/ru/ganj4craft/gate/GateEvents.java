package ru.ganj4craft.gate;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Серверные события для логирования и регистрации команд.
 */
public class GateEvents {

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        String name = event.getEntity().getName().getString();
        GanjaGate.LOGGER.info("[GanjaGate] Player {} entered the world (passed all checks)", name);
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("ganjagate")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(Commands.literal("reload").executes(ctx -> {
                    GateConfig.load();
                    int total = GateConfig.getWhitelist().size();
                    ctx.getSource().sendSuccess(
                        () -> Component.literal("§a[GanjaGate] Белый список модов успешно перезагружен! Всего разрешено: " + total),
                        true
                    );
                    GanjaGate.LOGGER.info("[GanjaGate] Whitelist reloaded via command (total: {})", total);
                    return 1;
                }))
        );
    }
}
