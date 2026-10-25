package de.lukas.donutsauctions.mixin

import de.lukas.donutsauctions.DonutsAuctionsClient
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientPacketListener
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket
import org.slf4j.LoggerFactory
import org.spongepowered.asm.mixin.Final
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo

@Mixin(ClientPacketListener::class)
abstract class ClientPlayNetworkHandlerMixin {

    @Shadow
    @Final
    private lateinit var minecraft: Minecraft

    @Inject(method = ["handleOpenScreen"], at = [At("HEAD")], cancellable = true)
    private fun suppressAutomationScreen(packet: ClientboundOpenScreenPacket, ci: CallbackInfo) {
        val rawTitle = packet.title.string
        if (!DonutsAuctionsClient.shouldSuppressAuctionScreenOpen(rawTitle)) {
            return
        }

        val player = minecraft.player ?: return
        try {
            val handler = packet.type.create(packet.containerId, player.inventory)
            player.containerMenu = handler
            DonutsAuctionsClient.onHeadlessAuctionScreenOpened(rawTitle, packet.containerId)
            ci.cancel()
        } catch (ex: Exception) {
            logger.error("Failed to open headless auction handler", ex)
            DonutsAuctionsClient.onHeadlessAuctionScreenClosed("Headless handler setup failed")
        }
    }

    private companion object {
        val logger = LoggerFactory.getLogger("DonutsAuctions")
    }
}
