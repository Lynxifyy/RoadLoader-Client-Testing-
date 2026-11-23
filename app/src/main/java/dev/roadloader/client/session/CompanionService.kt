package dev.roadloader.client.session

import dev.roadloader.core.CompanionServiceBase

/** Hosts the 32-bit engine in `android:process=":road32"`. */
class CompanionService : CompanionServiceBase() {
    override val channelId = "roadclient-engine"
    override val notificationTitle = "RoadClient engine"
}
