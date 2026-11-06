package ru.ganj4craft.gate;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GanjaGate — защитный мод для GanjaCraft (NeoForge 26.1.2).
 *
 * Сетевая античит-верификация модов во время Configuration phase:
 * 1. Сервер отправляет ModListRequestPayload с nonce
 * 2. Клиент отвечает ModListResponsePayload со списком modId
 * 3. Сервер проверяет по вайтлисту и кикает если есть запрещенные/лишние моды
 */
@Mod(GanjaGate.MOD_ID)
public class GanjaGate {
    public static final String MOD_ID = "ganjagate";
    public static final Logger LOGGER = LoggerFactory.getLogger("GanjaGate");

    public GanjaGate(IEventBus modBus, ModContainer modContainer) {
        LOGGER.info("[GanjaGate] Initializing GanjaGate network anti-cheat (NeoForge 26.1.2)...");
        modBus.addListener(this::onCommonSetup);
        modBus.addListener(GateNetwork::onRegisterPayloads);
        modBus.addListener(GateNetwork::onRegisterConfigurationTasks);
        NeoForge.EVENT_BUS.addListener(GateEvents::onPlayerLoggedIn);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        GateConfig.load();
        LOGGER.info("[GanjaGate] Whitelist loaded: {} mods allowed, strict={}", 
            GateConfig.getWhitelist().size(), GateConfig.isStrictMode());
    }
}
