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
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.config.Choice
import net.ccbluex.liquidbounce.config.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.render.BoxesRenderer
import net.ccbluex.liquidbounce.render.engine.Color4b
import net.ccbluex.liquidbounce.render.engine.Vec3
import net.ccbluex.liquidbounce.render.renderEnvironmentForWorld
import net.ccbluex.liquidbounce.render.utils.rainbow
import net.ccbluex.liquidbounce.render.withPosition
import net.ccbluex.liquidbounce.utils.block.AbstractBlockLocationTracker
import net.ccbluex.liquidbounce.utils.block.ChunkScanner
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction

/**
 * BlockESP module
 *
 * Allows you to see selected blocks through walls.
 */

object ModuleBlockESP : Module("BlockESP", Category.RENDER) {

    private val modes = choices("Mode", Box, arrayOf(Box))

    private val targetedBlocksSetting by blocks("Targets", hashSetOf(Blocks.DRAGON_EGG, Blocks.RED_BED))

    private val color by color("Color", Color4b(255, 179, 72, 255))
    private val colorRainbow by boolean("Rainbow", false)

    private object Box : Choice("Box") {
        override val parent: ChoiceConfigurable
            get() = modes

        private val outline by boolean("Outline", true)

        // todo: use box of block, not hardcoded
        private val box = Box(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)



        val renderHandler = handler<WorldRenderEvent> { event ->

            val matrixStack = event.matrixStack

            val base = if (colorRainbow) rainbow() else color
            val baseColor = base.alpha(50)
            val outlineColor = base.alpha(100)

            val boxRenderer = BoxesRenderer()



            renderEnvironmentForWorld(matrixStack) {
                try {
                    synchronized(BlockTracker.trackedBlockMap) {
                        for (pos in BlockTracker.trackedBlockMap.keys) {
                            val vec3 = Vec3(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())

                            withPosition(vec3) {

                                val facesToInclude = Direction.values().map {
                                    !BlockTracker.trackedBlockMap.containsKey(AbstractBlockLocationTracker.TargetBlockPos(pos.toBlockPos().offset(it)))
                                }
                                boxRenderer.drawBox(this, box, outline, facesToInclude)

                            }
                        }
                    }
                } finally {
                    boxRenderer.draw(this, baseColor, outlineColor)
                }


                }

        }

    }

    override fun enable() {
        ChunkScanner.subscribe(BlockTracker)
    }

    override fun disable() {
        ChunkScanner.unsubscribe(BlockTracker)
    }

    private object TrackedState

    private object BlockTracker : AbstractBlockLocationTracker<TrackedState>() {
        override fun getStateFor(pos: BlockPos, state: BlockState): TrackedState? {
            return if (targetedBlocksSetting.contains(state.block)) {
                TrackedState
            } else {
                null
            }
        }

    }

}
