/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2023 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.exploit.ModulePingSpoof
import net.ccbluex.liquidbounce.features.module.modules.player.ModuleBlink
import net.ccbluex.liquidbounce.render.drawLineStrip
import net.ccbluex.liquidbounce.render.engine.Color4b
import net.ccbluex.liquidbounce.render.engine.Vec3
import net.ccbluex.liquidbounce.render.renderEnvironmentForWorld
import net.ccbluex.liquidbounce.render.utils.rainbow
import net.ccbluex.liquidbounce.render.withColor
import net.ccbluex.liquidbounce.utils.client.Chronometer
import net.ccbluex.liquidbounce.utils.client.EventScheduler
import net.ccbluex.liquidbounce.utils.client.notification
import net.ccbluex.liquidbounce.utils.client.sendPacketSilently
import net.ccbluex.liquidbounce.utils.combat.countEnemies
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentPosition
import net.ccbluex.liquidbounce.utils.client.*
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket
import net.minecraft.network.packet.c2s.play.*
import net.minecraft.network.packet.c2s.query.QueryPingC2SPacket
import net.minecraft.network.packet.c2s.query.QueryRequestC2SPacket
import net.minecraft.network.packet.s2c.play.*
import net.minecraft.util.math.Vec3d

/**
 * BadWifi module
 *
 * Holds back packets to prevent you from being hit by an enemy.
 */
@Suppress("detekt:all")

object ModuleBadWifi : Module("BadWIFI", Category.COMBAT) {

    private val delay by int("Delay", 550, 0..1000)

    // if there is many player
    private val maxEnemiesToStop by int("MaxEnemiesToStop", 2, 2..10)
    private val recoilTime by int("RecoilTime", 750, 0..2000)

    private val color by color("Color", Color4b(255, 179, 72, 255))
    private val colorRainbow by boolean("Rainbow", false)
    private val packetQueue = LinkedHashSet<ModulePingSpoof.DelayData>()
    private val positions = LinkedHashSet<PositionData>()

    private val resetTimer = Chronometer()

    override fun enable() {
        if (ModuleBlink.enabled) {
            // Cannot disable on the moment it's enabled, so schedule module deactivation in the next few milliseconds.
            EventScheduler.schedule(this, GameRenderEvent::class.java, action = {
                this.enabled = false
            })

            notification("Compatibility error", "BadWIFI is incompatible with Blink", NotificationEvent.Severity.ERROR)
        }
    }

    override fun disable() {
        if (mc.player == null) {
            return
        }

        blink()
    }

    val packetHandler = handler<PacketEvent> { event ->
        if (player.isDead || event.isCancelled) {
            return@handler
        }

        val packet = event.packet

        if (!shouldLag()) {
            blink()
            return@handler
        }
        when (packet) {
            is HandshakeC2SPacket, is QueryRequestC2SPacket, is QueryPingC2SPacket, is ChatMessageS2CPacket, is DisconnectS2CPacket -> {
                return@handler
            }

            // Flush on doing action, getting action
            is PlayerPositionLookS2CPacket, is PlayerInteractBlockC2SPacket, is PlayerActionC2SPacket, is UpdateSignC2SPacket, is PlayerInteractEntityC2SPacket, is ResourcePackStatusC2SPacket -> {
                blink()
                return@handler
            }

            // Flush on kb
            is EntityVelocityUpdateS2CPacket -> {
                if (packet.id == player.id && (packet.velocityX != 0 || packet.velocityY != 0 || packet.velocityZ != 0)) {
                    blink()
                    return@handler
                }
            }

            is ExplosionS2CPacket -> {
                if (packet.playerVelocityX != 0f || packet.playerVelocityY != 0f || packet.playerVelocityZ != 0f) {
                    blink()
                    return@handler
                }
            }

            // Flush on damage
            is HealthUpdateS2CPacket -> {
                if (packet.health < player.health) {
                    blink()
                    return@handler
                }
            }
        }

        if (!resetTimer.hasElapsed(recoilTime.toLong())) {
            return@handler
        }

        if (event.origin == TransferOrigin.SEND) {
            event.cancelEvent()

            synchronized(packetQueue) {
                packetQueue.add(ModulePingSpoof.DelayData(packet, System.currentTimeMillis()))
            }
        }
    }

    val worldChangeHandler = handler<WorldChangeEvent> {
        // Clear packets on disconnect only
        if (it.world == null) {
            blink(false)
        }
    }

    val tickHandler = repeatable {
        if (player.isDead || player.isUsingItem) {
            blink()
            return@repeatable
        }

        if (!resetTimer.hasElapsed(recoilTime.toLong())) {
            return@repeatable
        }

        synchronized(positions) {
            positions.add(PositionData(player.pos, System.currentTimeMillis()))
        }

        handlePackets()
    }

    val renderHandler = handler<WorldRenderEvent> { event ->
        val matrixStack = event.matrixStack
        val color = if (colorRainbow) rainbow() else color

        synchronized(positions) {
            renderEnvironmentForWorld(matrixStack) {
                withColor(color) {
                    drawLineStrip(*makeLines(color, positions, event.partialTicks))
                }
            }
        }
    }

    private fun handlePackets() {
        synchronized(packetQueue) {
            packetQueue.removeIf {
                if (it.delay <= System.currentTimeMillis() - delay) {
                    sendPacketSilently(it.packet)

                    return@removeIf true
                }

                false
            }
        }

        synchronized(positions) {
            positions.removeIf { it.delay <= System.currentTimeMillis() - delay }
        }
    }

    private fun blink(handlePackets: Boolean = true) {
        synchronized(packetQueue) {
            if (handlePackets) {
                resetTimer.reset()

                packetQueue.removeIf {
                    sendPacketSilently(it.packet)

                    true
                }
            } else {
                packetQueue.clear()
            }
        }

        synchronized(positions) {
            positions.clear()
        }
    }

    private fun shouldLag(): Boolean {
        return world.countEnemies(0f..4f) <= maxEnemiesToStop
    }

    @JvmStatic
    internal fun makeLines(
        color: Color4b, positions: CopyOnWriteArrayList<PositionData>, tickDelta: Float
    ): Array<Vec3> {
        val mutableList = mutableListOf<Vec3>()
        positions.forEach {
            mutableList.add(Vec3(it.vec))
        }
        mutableList += player.interpolateCurrentPosition(tickDelta)
        return mutableList.toTypedArray()
    }
    
    data class PositionData(val vec: Vec3d, val delay: Long)
}
