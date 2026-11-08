package com.example.vemoring

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

@Composable
fun CarCharacter(modifier: Modifier = Modifier, mood: MascotMood = MascotMood.NORMAL) {
    Image(painterResource(mood.character), contentDescription = mood.description,
        modifier = modifier, contentScale = ContentScale.Fit)
}
