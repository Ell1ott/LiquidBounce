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
import net.ccbluex.liquidbounce.render.RenderEnvironment
import net.ccbluex.liquidbounce.render.drawGradientCircle
import net.ccbluex.liquidbounce.render.engine.Color4b
import net.ccbluex.liquidbounce.render.renderEnvironmentForWorld
import net.ccbluex.liquidbounce.render.withPosition
import net.ccbluex.liquidbounce.utils.entity.interpolateCurrentPosition
import net.minecraft.entity.TntEntity
import net.minecraft.entity.decoration.EndCrystalEntity
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

    val creepers by boolean("Creepers", true)
    val tnt by boolean("TNT", true)
    val endCrystal by boolean("EndCrystal", false)

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

        val entitiesWithBoxes = world.entities.groupBy { entity ->
            if(player.squaredDistanceTo(entity) > rangeSquared) return@groupBy 0
            when(entity) {
                is CreeperEntity -> if (creepers) 3 else 0
                is TntEntity -> if (tnt) 4 else 0
                is EndCrystalEntity -> if(endCrystal) 6 else 0
                else -> 0
            }
        }

        renderEnvironmentForWorld(matrixStack) {
            for ((power, entities) in entitiesWithBoxes) {
                if (power == 0) continue
                for (entity in entities) {
                    val pos = entity.interpolateCurrentPosition(event.partialTicks)

                    withPosition(pos) {
                        val isFused = entity is CreeperEntity && entity.getClientFuseTime(event.partialTicks) <= 0
                        drawExplosionCircle(this, power, isFused)

                    }
                }
            }
        }
    }

    private fun drawExplosionCircle(renderEnvironment: RenderEnvironment, power: Int, fused: Boolean) {
        with(renderEnvironment) {
            if(fused) {
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
