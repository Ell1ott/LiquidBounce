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

import net.ccbluex.liquidbounce.config.Configurable
import net.ccbluex.liquidbounce.event.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.render.drawGradientCircle
import net.ccbluex.liquidbounce.render.engine.Color4b
import net.ccbluex.liquidbounce.render.renderEnvironmentForWorld
import net.ccbluex.liquidbounce.render.withPosition
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentPosition
import net.minecraft.entity.TntEntity
import net.minecraft.entity.mob.CreeperEntity

/**
 * ESP module
 *
 * Allows you to see targets through walls.
 */

object ModuleExplosion : Module("Explosion", Category.RENDER) {

    private object Fused : Configurable("Fused") {
        val outerColor by color("OuterColor", Color4b(255, 0, 0, 50))
        val innerColor by color("InnerColor", Color4b(255, 0, 0, 0))
    }

    private object NotFused : Configurable("NotFused") {
        val outerColor by color("OuterColor", Color4b(255, 153, 0, 50))
        val innerColor by color("InnerColor", Color4b(255, 153, 0, 0))
    }

    init {
        tree(Fused)
        tree(NotFused)
    }

    private val range by int("Range", 20, 1..100).listen {
        rangeSquared = it*it
        it
    }
    private var rangeSquared: Int = range* range



    private val deadEntities by boolean("DeadEntities", false)



    val renderHandler = handler<WorldRenderEvent> { event ->
        val matrixStack = event.matrixStack

        val entitiesWithBoxes = findRenderedEntities().groupBy { entity ->
            when(entity) {
                is CreeperEntity -> 3
                is TntEntity -> 4
                else -> 0
            }
        }

        renderEnvironmentForWorld(matrixStack) {
            entitiesWithBoxes.forEach { power, entities ->
                for (entity in entities) {
                    val pos = entity.interpolateCurrentPosition(event.partialTicks)

                    withPosition(pos) {
                        if(entity is CreeperEntity && entity.getClientFuseTime(event.partialTicks) <= 0) {
                            drawGradientCircle(
                                power.toFloat() * 2f,
                                power.toFloat() * 1.5f,
                                NotFused.outerColor,
                                NotFused.innerColor)
                        } else {
                            drawGradientCircle(
                                power.toFloat() * 2f,
                                power.toFloat() * 1.5f,
                                Fused.outerColor,
                                Fused.innerColor)
                        }

                    }
                }
            }
        }
    }


    fun findRenderedEntities() = world.entities.filter {
        (it is CreeperEntity || it is TntEntity)
            && (it.isAlive || deadEntities)
            && player.squaredDistanceTo(it) < rangeSquared
    }


}
