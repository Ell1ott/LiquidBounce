/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2016 - 2022 CCBlueX
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

import net.ccbluex.liquidbounce.config.Choice
import net.ccbluex.liquidbounce.config.ChoiceConfigurable
import net.ccbluex.liquidbounce.config.ToggleableConfigurable
import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.world.ModuleScaffold
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationsConfigurable
import net.ccbluex.liquidbounce.utils.entity.eyes
import net.ccbluex.liquidbounce.utils.aiming.raycast
import net.ccbluex.liquidbounce.utils.block.getBlock
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.entity.FallingPlayer
import net.minecraft.block.Blocks
import net.minecraft.item.Item
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.math.abs
import kotlin.booleanArrayOf

/**
 * NoFall module
 *
 * Protects you from taking fall damage.
 */

object ModuleNoFall : Module("NoFall", Category.PLAYER) {

    private val modes = choices(
        "Mode", SpoofGround, arrayOf(
            SpoofGround, NoGround, Packet, MLG
        )
    )

    private object SpoofGround : Choice("SpoofGround") {

        override val parent: ChoiceConfigurable
            get() = modes

        val packetHandler = handler<PacketEvent> {
            val packet = it.packet

            if (packet is PlayerMoveC2SPacket) {
                packet.onGround = true
            }

        }

    }

    private object NoGround : Choice("NoGround") {

        override val parent: ChoiceConfigurable
            get() = modes

        val packetHandler = handler<PacketEvent> {
            val packet = it.packet

            if (packet is PlayerMoveC2SPacket) {
                packet.onGround = false
            }

        }

    }

    private object Packet : Choice("Packet") {

        override val parent: ChoiceConfigurable
            get() = modes

        val repeatable = repeatable {
            if (player.fallDistance > 2f) {
                network.sendPacket(PlayerMoveC2SPacket.OnGroundOnly(true))
            }
        }

    }

    private object MLG : Choice("MLG") {
        override val parent: ChoiceConfigurable
            get() = modes

        val minFallDist by float("MinFallDistance", 5f, 2f..50f)

        val pickup by boolean("pickup", true)
        val pickupDelay by intRange("pickupDelay", 3..4, 1..20)

        val rotationsConfigurable = tree(RotationsConfigurable())

        var currentTarget: ModuleScaffold.Target? = null

        var waterplaced: Boolean = false

        val itemForMLG
            get() = findClosestItem(
                arrayOf(
                    Items.WATER_BUCKET, Items.COBWEB, Items.POWDER_SNOW_BUCKET, Items.HAY_BLOCK, Items.SLIME_BLOCK
                )
            )
        var pos: Vec3d? = null
        val tickMovementHandler = handler<PlayerNetworkMovementTickEvent> {
            if(waterplaced){
                RotationManager.aimAt(RotationManager.makeRotation(pos!!, player.eyes), configurable = rotationsConfigurable)
            }


            if (it.state != EventState.PRE || player.fallDistance <= minFallDist || itemForMLG == null) {
                return@handler
            }

            val collision = FallingPlayer.fromPlayer(player).findCollision(20)?.pos ?: return@handler

            if (collision.getBlock() in arrayOf(
                    Blocks.WATER, Blocks.COBWEB, Blocks.POWDER_SNOW, Blocks.HAY_BLOCK, Blocks.SLIME_BLOCK
                )
            ) {
                return@handler
            }


            currentTarget = ModuleScaffold.updateTarget(collision.up())


            val target = currentTarget ?: return@handler

            pos = target.facepos
            RotationManager.aimAt(target.rotation, configurable = rotationsConfigurable)
        }

        val repeatable = repeatable {
            val target = currentTarget ?: return@repeatable
            val rotation = RotationManager.currentRotation ?: return@repeatable

            val rayTraceResult = raycast(4.5, rotation) ?: return@repeatable

            if (rayTraceResult.type != HitResult.Type.BLOCK || rayTraceResult.blockPos != target.blockPos || rayTraceResult.side != target.direction) {
                return@repeatable
            }
            if(waterplaced){
                doPlacement(rayTraceResult, true)
                waterplaced = false
                currentTarget = null
                pos = null
                SilentHotbar.resetSlot(this) //swaps back after remving the water
                return@repeatable

            }
            val item = itemForMLG ?: return@repeatable

            if(player.inventory.getStack(item).item.equals(Items.WATER_BUCKET)){
                SilentHotbar.selectSlotSilently(this, item, 100)
                doPlacement(rayTraceResult, true)
                waterplaced = true
                wait(pickupDelay.random())

            } else {
                SilentHotbar.selectSlotSilently(this, item, 1)
                doPlacement(rayTraceResult, false)
                currentTarget = null

            }



        }

        private fun findClosestItem(items: Array<Item>) = (0..8).filter { player.inventory.getStack(it).item in items }
            .minByOrNull { abs(player.inventory.selectedSlot - it) }

        private fun doPlacement(rayTraceResult: BlockHitResult, isWater: Boolean) {
            val stack = player.mainHandStack
            val count = stack.count


            if(isWater){
                if (!stack.isEmpty) {
                    val interactItem = interaction.interactItem(player, Hand.MAIN_HAND)

                    if (interactItem.isAccepted) {
                        if (interactItem.shouldSwingHand()) {
                            player.swingHand(Hand.MAIN_HAND)
                        }

                        mc.gameRenderer.firstPersonRenderer.resetEquipProgress(Hand.MAIN_HAND)
                        return
                    }

                }
            } else {
                val interactBlock = interaction.interactBlock(player, Hand.MAIN_HAND, rayTraceResult)

                if (interactBlock.isAccepted) {
                    if (interactBlock.shouldSwingHand()) {
                        player.swingHand(Hand.MAIN_HAND)

                        if (!stack.isEmpty && (stack.count != count || interaction.hasCreativeInventory())) {
                            mc.gameRenderer.firstPersonRenderer.resetEquipProgress(Hand.MAIN_HAND)
                        }
                    }

                    return
                } else if (interactBlock == ActionResult.FAIL) {
                    return
                }
            }
        }
    }

}
