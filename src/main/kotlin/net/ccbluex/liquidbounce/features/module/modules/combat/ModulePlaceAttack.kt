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
import net.ccbluex.liquidbounce.utils.aiming.raycast
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.combat.TargetTracker
import net.ccbluex.liquidbounce.utils.entity.*
import net.minecraft.block.Block
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen
import net.minecraft.client.gui.screen.ingame.InventoryScreen
import net.minecraft.block.Blocks
import net.minecraft.item.Item
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.*
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.HitResult
import kotlin.math.floor
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
    private val pauseKillaura by boolean("PauseKillaura", true)
    private val self by boolean("Self", false)
    private val earlySwap by boolean("EarlySwap", true)

    // Target
    private val targetTracker = tree(TargetTracker())

    // Rotation
    private val rotations = tree(RotationsConfigurable())

    // Predict
    private val predict by floatRange("Predict", 0f..0f, 0f..5f)

    // Bypass techniques
    private val clientSwing by boolean("Swing", true)
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

    val blocks = arrayListOf<Block>(Blocks.LAVA, Blocks.COBWEB)

    val itemForMLG
        get() = findClosestItem(
            arrayOf(
                Items.LAVA_BUCKET, Items.COBWEB
            )
        )

    var waskillAuraOn = false

    override fun disable() {
        targetTracker.cleanup()
        if(pauseKillaura && waskillAuraOn){
            ModuleKillAura.enabled = true
        }
    }
    override fun enable() {

        if(pauseKillaura){
            waskillAuraOn = ModuleKillAura.enabled
            ModuleKillAura.enabled = false
        }
    }

    private fun findClosestItem(items: Array<Item>) = (0..8).filter { player.inventory.getStack(it).item in items }
        .minByOrNull { abs(player.inventory.selectedSlot - it) }


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

        if(player.isBlocking){
            if(!whileBlocking.enabled){
                return@repeatable
            }
        } else if(player.isUsingItem() && !whileUsingItem){
            return@repeatable
        }
        val slot = itemForMLG ?: return@repeatable
        val item = player.inventory.getStack(slot).item
        if(earlySwap){
            SilentHotbar.selectSlotSilently(this, slot, 2, true)
        }

        val rayTraceResult = raycast(4.5, rotation) ?: return@repeatable
        if (rayTraceResult.type != HitResult.Type.BLOCK || rayTraceResult.blockPos.offset(rayTraceResult.side) != targetBlockPos) {
            return@repeatable
        }


        SilentHotbar.selectSlotSilently(this, slot, swapBackDelay.random(), true)
        if(doPlacement(rayTraceResult, item == Items.LAVA_BUCKET) && disableAfterPlacement){
            disable()
            enabled = false
        }
    }
    private fun swing(clientSide: Boolean) {
        if(clientSide) {
            player.swingHand(Hand.MAIN_HAND)
        }else {
            network.sendPacket(HandSwingC2SPacket(Hand.MAIN_HAND))
        }

    }
    private fun doPlacement(rayTraceResult: BlockHitResult, isWater: Boolean): Boolean {
        val stack = player.mainHandStack
        val count = stack.count


        if(isWater){
            if (!stack.isEmpty) {
                val interactItem = interaction.interactItem(player, Hand.MAIN_HAND)

                if (interactItem.isAccepted) {
                    if (interactItem.shouldSwingHand()) {
                        swing(clientSwing)
                    }

                    mc.gameRenderer.firstPersonRenderer.resetEquipProgress(Hand.MAIN_HAND)
                    return true
                }

            }
        } else {
            val interactBlock = interaction.interactBlock(player, Hand.MAIN_HAND, rayTraceResult)

            if (interactBlock.isAccepted) {
                if (interactBlock.shouldSwingHand()) {
                    swing(clientSwing)

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

            val targetpos = target.pos.add(targetPrediction.normalize())
            // aim at targets feat
            val targetBPos = BlockPos(
                floor(targetpos.x).toInt(),
                floor(targetpos.y).toInt(),
                floor(targetpos.z).toInt()
            )

            if(blocks.contains(targetBPos?.getState()?.block) || (Vec3d.ofCenter(targetBPos).distanceTo(player.pos) < 1 && !self)){
                continue
            }
            targetBlockPos = targetBPos

            val rotation = RotationManager.makeRotation(targetpos, eyes)
            RotationManager.aimAt(rotation, configurable = rotations)
            break
        }
    }

}
