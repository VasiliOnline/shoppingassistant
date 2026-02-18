package com.example.shoppingassistant.feature.pages.offers.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.shoppingassistant.feature.ui.cards.CardDensity
import com.example.shoppingassistant.feature.ui.cards.CompactCardSkeleton
import com.example.shoppingassistant.feature.ui.cards.OfferCardSkeleton

@Composable
fun OffersSkeleton(
    horizontal: Boolean,
    cardStyle: OfferCardStyle = OfferCardStyle.Tiles,
    modifier: Modifier = Modifier,
) {
    val density = if (cardStyle == OfferCardStyle.Flat) CardDensity.Dense else CardDensity.Regular

    if (horizontal) {
        LazyRow(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(3) {
                CompactCardSkeleton(
                    modifier = Modifier.width(260.dp),
                    density = density,
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(3) {
                OfferCardSkeleton(
                    modifier = Modifier.fillMaxWidth(),
                    density = density,
                )
            }
        }
    }
}
