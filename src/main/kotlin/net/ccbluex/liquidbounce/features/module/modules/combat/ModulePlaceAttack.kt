/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2016 - 2023 CCBlueX
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

// import net.ccbluex.liquidbounce.config.NamedChoice
import net.ccbluex.liquidbounce.config.ToggleableConfigurable
import net.ccbluex.liquidbounce.event.*
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleKillAura.RaycastMode.*
import net.ccbluex.liquidbounce.utils.block.getState
import net.ccbluex.liquidbounce.utils.aiming.RotationManager
import net.ccbluex.liquidbounce.utils.aiming.RotationsConfigurable
import net.ccbluex.liquidbounce.utils.aiming.facingEnemy
import net.ccbluex.liquidbounce.utils.aiming.raytraceEntity
import net.ccbluex.liquidbounce.utils.aiming.raycast
import net.ccbluex.liquidbounce.utils.client.MC_1_8
import net.ccbluex.liquidbounce.utils.client.protocolVersion
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.combat.CpsScheduler
import net.ccbluex.liquidbounce.utils.combat.TargetTracker
import net.ccbluex.liquidbounce.utils.combat.shouldBeAttacked
import net.ccbluex.liquidbounce.utils.entity.*
import net.ccbluex.liquidbounce.utils.item.openInventorySilently
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen
import net.minecraft.client.gui.screen.ingame.InventoryScreen
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityGroup
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.block.Blocks
import net.minecraft.item.Item
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.*
import net.minecraft.util.Hand
import net.minecraft.util.ActionResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.util.math.Vec3i
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.world.GameMode
import kotlin.math.sqrt
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.math.abs

/**
 * KillAura module
 *
 * places dameging blocks at enemies feat
 */
object ModulePlaceAttack : Module("PlaceAttack", Category.COMBAT) {

    // Attack speed
    // private val cps by intRange("CPS", 5..8, 1..20)
    // Range
    private val range by float("Range", 4.2f, 1f..8f)
    private val scanExtraRange by float("ScanExtraRange", 3.0f, 0.0f..7.0f)

    private val disableAfterPlacement by boolean("DisableAfterPlacement", true)
    private val swapBackDelay by intRange("SwapBackDelay", 1..3, 1..20)


    // Target
    private val targetTracker = tree(TargetTracker())

    // Rotation
    private val rotations = tree(RotationsConfigurable())

    // Predict
    private val predict by floatRange("Predict", 0f..0f, 0f..5f)

    // Bypass techniques
    private val swing by boolean("Swing", true)
    // private val keepSprint by boolean("KeepSprint", true)
    // private val unsprintOnCrit by boolean("UnsprintOnCrit", true)
    // private val attackShielding by boolean("AttackShielding", false)

    private val whileUsingItem by boolean("whileUsingItem", true)
    object whileBlocking : ToggleableConfigurable(this, "whileBlocking", true) {
        val blockingTicks by int("blockingTicks", 0, 0..20)
    }

    init {
        tree(whileBlocking)
    }


    private val ignoreOpenInventory by boolean("IgnoreOpenInventory", true)
    private val simulateInventoryClosing by boolean("SimulateInventoryClosing", true)

    val itemForMLG
        get() = findClosestItem(
            arrayOf(
                Items.LAVA_BUCKET, Items.COBWEB
            )
        )



    override fun disable() {
        targetTracker.cleanup()
    }

    private fun findClosestItem(items: Array<Item>) = (0..8).filter { player.inventory.getStack(it).item in items }
            .minByOrNull { abs(player.inventory.selectedSlot - it) }

//    val renderHandler = handler<EngineRenderEvent> {
//        val currentTarget = targetTracker.lockedOnTarget ?: return@handler
//
//        val bb = currentTarget.boundingBox
//
//        val renderTask = ColoredPrimitiveRenderTask(6 * 10 * 10 * 2, PrimitiveType.Lines)
//
//        for (direction in Direction.values()) {
//            val maxRaysOnAxis = 10 - 1
//            val stepFactor = 1.0 / maxRaysOnAxis;
//
//            val face = bb.getFace(direction)
//
//            val outerPoints = face.getAllPoints(Vec3d.of(direction.vector))
//
//            var idx = 0
//
//            for (outerPoint in outerPoints) {
//                val vex = Vec3(outerPoint) - Vec3(
//                    0.0, 0.0, 1.0
//                )
//                val color = Color4b(Color.getHSBColor(idx / 4.0f, 1.0f, 1.0f))
//
//                renderTask.index(renderTask.vertex(vex, Color4b.WHITE))
//                renderTask.index(renderTask.vertex(vex + Vec3(direction.vector), color))
//
//                idx++
//            }
//
//            //            for (x in (0..maxRaysOnAxis)) {
//            //                for (y in (0..maxRaysOnAxis)) {
//            //                    renderTask.index(renderTask.vertex(Vec3(plane.getPoint(x * stepFactor, y * stepFactor)) - Vec3(0.0, 0.0, 1.0), Color4b.WHITE))
//            //                }
//            //            }
//        }
//
//        RenderEngine.enqueueForRendering(RenderEngine.CAMERA_VIEW_LAYER, renderTask)
//    }

    val rotationUpdateHandler = handler<PlayerNetworkMovementTickEvent> {
        // Killaura in spectator-mode is pretty useless, trust me.
        if (it.state != EventState.PRE || player.isSpectator) {
            return@handler
        }

        // Make sure killaura-logic is not running while inventory is open
        val isInInventoryScreen = mc.currentScreen is InventoryScreen || mc.currentScreen is GenericContainerScreen

        if (isInInventoryScreen && !ignoreOpenInventory) {
            // Cleanup current target tracker
            targetTracker.cleanup()
            return@handler
        }

        // Update current target tracker to make sure you attack the best enemy
        updateEnemySelection()
    }

    val repeatable = repeatable {
        val isInInventoryScreen = mc.currentScreen is InventoryScreen
        // Check if there is target to attack
        val target = targetTracker.lockedOnTarget ?: return@repeatable
        // Did you ever send a rotation before?
        val rotation = RotationManager.currentRotation ?: return@repeatable

        val rayTraceResult = raycast(4.5, rotation) ?: return@repeatable
        if (rayTraceResult.type != HitResult.Type.BLOCK || rayTraceResult.blockPos.offset(rayTraceResult.side) != targetBlockPos) {
            return@repeatable
        }
        val slot = itemForMLG ?: return@repeatable
        val item = player.inventory.getStack(slot).item

        SilentHotbar.selectSlotSilently(this, slot, swapBackDelay.random())
        if(doPlacement(rayTraceResult, item == Items.LAVA_BUCKET) && disableAfterPlacement){
            enabled = false
        }

        // if (target.boxedDistanceTo(player) <= range && facingEnemy(
        //         target, rotation, range.toDouble(), wallRange.toDouble()
        //     )
        // ) {
        //     // Check if between enemy and player is another entity
        //     val raycastedEntity = raytraceEntity(range.toDouble(), rotation, filter = {
        //         when (raycast) {
        //             TRACE_NONE -> false
        //             TRACE_ONLYENEMY -> it.shouldBeAttacked()
        //             TRACE_ALL -> true
        //         }
        //     }) ?: target

        //     // Swap enemy if there is a better enemy
        //     // todo: compare current target to locked target
        //     if (raycastedEntity.shouldBeAttacked() && raycastedEntity != target) {
        //         targetTracker.lock(raycastedEntity)
        //     }

        //     // Attack enemy according to cps and cooldown
        //     val clicks = cpsTimer.clicks(condition = {
        //         (!cooldown || player.getAttackCooldownProgress(0.0f) >= 1.0f) && (!ModuleCriticals.shouldWaitForCrit() || raycastedEntity.velocity.lengthSquared() > 0.25 * 0.25) && (attackShielding || raycastedEntity !is PlayerEntity || player.mainHandStack.item !is AxeItem || !raycastedEntity.wouldBlockHit(
        //             player
        //         ))
        //     }, cps)

        //     repeat(clicks) {
        //         if (simulateInventoryClosing && isInInventoryScreen) {
        //             network.sendPacket(CloseHandledScreenC2SPacket(0))
        //         }

        //         val blocking = player.isBlocking
        //         // if((blocking && !whileBlocking.enabled) || (player.isUsingItem()) && !whileUsingItem){
        //         //     return@repeat
        //         // }

        //         if(blocking){
        //             if(!whileBlocking.enabled){
        //                 return@repeat
        //             }
        //         } else if(player.isUsingItem() && !whileUsingItem){
        //             return@repeat
        //         }


        //         // Make sure to unblock now
        //         if (blocking) {
        //             network.sendPacket(
        //                 PlayerActionC2SPacket(
        //                     PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN
        //                 )
        //             )

        //             // Wait until the un-blocking delay time is up
        //             if (whileBlocking.blockingTicks > 0) {
        //                 mc.options.useKey.isPressed = false
        //                 wait(whileBlocking.blockingTicks)
        //             }
        //         }
        //         // Fail rate
        //         if (failRate > 0 && failRate > Random.nextInt(100)) {
        //             // Fail rate should always make sure to swing the hand, so the server-side knows you missed the enemy.
        //             if (swing) {
        //                 player.swingHand(Hand.MAIN_HAND)
        //             } else {
        //                 network.sendPacket(HandSwingC2SPacket(Hand.MAIN_HAND))
        //             }

        //             // todo: might notify client-user about fail hit
        //         } else {
        //             // Attack enemy
        //             attackEntity(raycastedEntity)
        //         }

        //         // Make sure to block again
        //         if (blocking) {
        //             // Wait until the blocking delay time is up
        //             if (whileBlocking.blockingTicks > 0) {
        //                 wait(whileBlocking.blockingTicks)
        //             }

        //             interaction.sendSequencedPacket(world) { sequence ->
        //                 PlayerInteractItemC2SPacket(player.activeHand, sequence)
        //             }
        //             mc.options.useKey.isPressed = true

        //             if (simulateInventoryClosing && isInInventoryScreen) {
        //                 openInventorySilently()
        //             }

        //         }
        //     }
        // }
    }

    private fun doPlacement(rayTraceResult: BlockHitResult, isWater: Boolean): Boolean {
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
                    return true
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

                return true
            } else if (interactBlock == ActionResult.FAIL) {
                return false
            }
        }
        return false
    }

    var targetBlockPos: BlockPos? = null
    /**
     * Update enemy on target tracker
     */
    private fun updateEnemySelection() {
        val rangeSquared = range * range

        targetTracker.validateLock { it.squaredBoxedDistanceTo(player) <= rangeSquared }

        val eyes = player.eyes

        val scanRange = if (targetTracker.maxDistanceSquared > rangeSquared) {
            ((range + scanExtraRange) * (range + scanExtraRange)).toDouble()
        } else {
            rangeSquared.toDouble()
        }

        for (target in targetTracker.enemies()) {
            if (target.squaredBoxedDistanceTo(player) > scanRange) {
                continue
            }

            val predictedTicks = predict.start + (predict.endInclusive - predict.start) * Math.random()

            val targetPrediction = Vec3d(
                target.x - target.prevX, target.y - target.prevY, target.z - target.prevZ
            ).multiply(predictedTicks)

            val playerPrediction = Vec3d(
                player.x - player.prevX, player.y - player.prevY, player.z - player.prevZ
            ).multiply(predictedTicks)

            // val box = target.box.offset(targetPrediction)

            // find best spot
            // val spot = RotationManager.raytraceBox(
            //     eyes.add(playerPrediction), box, range = sqrt(scanRange), wallsRange = wallRange.toDouble()
            // ) ?: continue

            // lock on target tracker
            targetTracker.lock(target)

            val targetpos = target.pos.add(targetPrediction)
            // aim at targets feat
            targetBlockPos = BlockPos(
                floor(targetpos.x).toInt(),
                floor(targetpos.y).toInt(),
                floor(targetpos.z).toInt()
                )

            val state = targetBlockPos?.getState()

            if (state?.block == Blocks.LAVA) {
                continue
            }
            val rotation = RotationManager.makeRotation(targetpos, eyes)
            RotationManager.aimAt(rotation, configurable = rotations)
            break
        }
    }



}
