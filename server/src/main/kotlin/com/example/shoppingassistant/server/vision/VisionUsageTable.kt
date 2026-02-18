package com.example.shoppingassistant.server.vision

import org.jetbrains.exposed.sql.Table

object VisionUsageTable : Table("vision_usage") {
    val userKey = varchar("user_key", length = 128)
    val remainingToday = integer("remaining_today")
    val remainingTotal = integer("remaining_total")
    val resetAtMillis = long("reset_at_millis")
    val updatedAtMillis = long("updated_at_millis")
    override val primaryKey = PrimaryKey(userKey)
}
