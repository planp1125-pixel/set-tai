package com.combo.monkeycompanion.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.combo.monkeycompanion.character.FacialHairStyle
import com.combo.monkeycompanion.character.HairStyle
import com.combo.monkeycompanion.character.OutfitStyle

@Composable
internal fun StudioAppearanceControls(
    facialHair: FacialHairStyle, hair: HairStyle, outfit: OutfitStyle,
    glasses: Boolean, cape: Boolean, bindi: Boolean, bow: Boolean, jasmine: Boolean, wings: Boolean,
    onFacialHair: (FacialHairStyle) -> Unit, onHairStyle: (HairStyle) -> Unit, onOutfit: (OutfitStyle) -> Unit,
    onGlasses: (Boolean) -> Unit, onCape: (Boolean) -> Unit, onBindi: (Boolean) -> Unit,
    onBow: (Boolean) -> Unit, onJasmine: (Boolean) -> Unit, onWings: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Facial hair", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FacialHairStyle.entries.forEach { FilterChip(selected = facialHair == it, onClick = { onFacialHair(it) }, label = { Text(it.label) }) }
        }
        Text("Hair style", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HairStyle.entries.forEach { FilterChip(selected = hair == it, onClick = { onHairStyle(it) }, label = { Text(it.label) }) }
        }
        Text("Outfit", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutfitStyle.entries.forEach { FilterChip(selected = outfit == it, onClick = { onOutfit(it) }, label = { Text(it.label) }) }
        }
        Text("Accessories", style = MaterialTheme.typography.titleSmall)
        listOf(
            Triple("Glasses / Shades", glasses, onGlasses), Triple("Hero cape", cape, onCape),
            Triple("Pottu / Bindi", bindi, onBindi), Triple("Hair ribbons / bow", bow, onBow),
            Triple("Jasmine flowers", jasmine, onJasmine), Triple("Fairy / hero wings", wings, onWings)
        ).forEach { (label, checked, onChange) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, modifier = Modifier.weight(1f))
                Switch(checked = checked, onCheckedChange = onChange)
            }
        }
    }
}
