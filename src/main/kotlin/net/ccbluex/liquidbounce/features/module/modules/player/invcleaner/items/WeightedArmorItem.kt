package net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.items

import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.ItemCategory
import net.ccbluex.liquidbounce.features.module.modules.player.invcleaner.ItemType
import net.ccbluex.liquidbounce.utils.item.ArmorComparator
import net.ccbluex.liquidbounce.utils.item.ArmorConfigurable
import net.ccbluex.liquidbounce.utils.item.ArmorPiece
import net.minecraft.item.ItemStack

class WeightedArmorItem(itemStack: ItemStack, slot: Int) : WeightedItem(itemStack, slot) {
    private val armorPiece = ArmorPiece(itemStack, slot)

    override val category: ItemCategory
        get() = ItemCategory(ItemType.ARMOR, armorPiece.entitySlotId)

//    override fun compareTo(other: WeightedItem): Int =
//        ArmorComparator(config).compare(this.armorPiece, (other as WeightedArmorItem).armorPiece)
    override fun compareToUsingArmorConfig(other: WeightedItem, config: ArmorConfigurable): Int =
        ArmorComparator(config).compare(this.armorPiece, (other as WeightedArmorItem).armorPiece)

}
