/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2024 CCBlueX
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
package net.ccbluex.liquidbounce.features.module.modules.player

import net.ccbluex.liquidbounce.config.NamedChoice
import net.ccbluex.liquidbounce.config.ToggleableConfigurable
import net.ccbluex.liquidbounce.event.events.*
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.repeatable
import net.ccbluex.liquidbounce.features.fakelag.FakeLag
import net.ccbluex.liquidbounce.features.fakelag.FakeLag.findAvoidingArrowPosition
import net.ccbluex.liquidbounce.features.fakelag.FakeLag.getInflictedHit
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.render.engine.Color4b
import net.ccbluex.liquidbounce.render.utils.rainbow
import net.ccbluex.liquidbounce.utils.client.notification
import net.ccbluex.liquidbounce.utils.kotlin.EventPriorityConvention
import net.ccbluex.liquidbounce.utils.math.component1
import net.ccbluex.liquidbounce.utils.math.component2
import net.ccbluex.liquidbounce.utils.math.component3
import net.minecraft.client.network.OtherClientPlayerEntity
import net.minecraft.entity.Entity
import net.minecraft.network.packet.c2s.play.*
import net.minecraft.util.math.Vec3d
import java.util.*

/**
 * Blink module
 *
 * Makes it look as if you were teleporting to other players.
 */

object ModuleBlink : Module("Blink", Category.PLAYER) {

    private val dummy by boolean("Dummy", false)
    private val ambush by boolean("Ambush", false)
    private val evadeArrows by boolean("EvadeArrows", true)

    private object BreadcrumbsOption : ToggleableConfigurable(this, "Breadcrumbs", true) {

        val breadcrumbsColor by color("BreadcrumbsColor", Color4b(255, 179, 72, 255))
        val breadcrumbsRainbow by boolean("BreadcrumbsRainbow", false)

        val renderHandler = handler<WorldRenderEvent> { event ->
            val matrixStack = event.matrixStack
            val color = if (breadcrumbsRainbow) rainbow() else breadcrumbsColor
            FakeLag.drawStrip(matrixStack, color)
        }

    }

    private object AutoResetOption : ToggleableConfigurable(this, "AutoReset", false) {
        val resetAfter by int("ResetAfter", 100, 1..1000)
        val action by enumChoice("ResetAction", ResetAction.RESET, ResetAction.values())
    }

    private var dummyPlayer: OtherClientPlayerEntity? = null

    init {
        tree(BreadcrumbsOption)
        tree(AutoResetOption)
    }

    override fun enable() {
        if (dummy) {
            val clone = OtherClientPlayerEntity(world, player.gameProfile)

            clone.headYaw = player.headYaw
            clone.copyPositionAndRotation(player)
            /**
             * A different UUID has to be set, to avoid [dummyPlayer] from being invisible to [player]
             * @see net.minecraft.world.entity.EntityIndex.add
             */
            clone.uuid = UUID.randomUUID()
            world.addEntity(clone)

            dummyPlayer = clone
        }
    }

    override fun disable() {
        FakeLag.flush()
        removeClone()
    }

    private fun removeClone() {
        val clone = dummyPlayer ?: return

        world.removeEntity(clone.id, Entity.RemovalReason.DISCARDED)
        dummyPlayer = null
    }

    val packetHandler = handler<PacketEvent>(priority = EventPriorityConvention.MODEL_STATE) { event ->
        val packet = event.packet

        if (event.isCancelled || event.origin != TransferOrigin.SEND) {
            return@handler
        }

        if (ambush && packet is PlayerInteractEntityC2SPacket) {
            enabled = false
            return@handler
        }
    }

    val repeatable = repeatable {
        if (evadeArrows) {
            val (x, y, z) = FakeLag.firstPosition() ?: return@repeatable

            if (getInflictedHit(Vec3d(x, y, z)) == null) {
                return@repeatable
            }

            val evadingPacket = findAvoidingArrowPosition()

            // We have found no packet that avoids getting hit? Then we default to blinking.
            // AutoDoge might save the situation...
            if (evadingPacket == null) {
                notification("Blink", "Unable to evade arrow. Blinking.",
                    NotificationEvent.Severity.INFO)
                enabled = false
            } else if (evadingPacket.ticksToImpact != null) {
                notification("Blink", "Trying to evade arrow...", NotificationEvent.Severity.INFO)
                FakeLag.flush(evadingPacket.idx + 1)
            } else {
                notification("Blink", "Arrow evaded.", NotificationEvent.Severity.INFO)
                FakeLag.flush(evadingPacket.idx + 1)
            }
        }
    }

    val playerMoveHandler = handler<PlayerMovementTickEvent> {
        if (AutoResetOption.enabled && FakeLag.positions.count() > AutoResetOption.resetAfter) {
            when (AutoResetOption.action) {
                ResetAction.RESET -> FakeLag.cancel()
                ResetAction.BLINK -> FakeLag.flush()
            }

            notification("Blink", "Auto reset", NotificationEvent.Severity.INFO)
            enabled = false
        }
    }

    enum class ResetAction(override val choiceName: String) : NamedChoice {
        RESET("Reset"),
        BLINK("Blink");
    }
}
