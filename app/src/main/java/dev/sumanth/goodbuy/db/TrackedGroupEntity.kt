package dev.sumanth.goodbuy.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.sumanth.goodbuy.model.TrackedGroup
import org.json.JSONArray

@Entity(tableName = "tracked_groups")
data class TrackedGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val productIdsJson: String
)

fun TrackedGroupEntity.toDomainModel(): TrackedGroup {
    val pIds = mutableListOf<String>()
    if (productIdsJson.isNotBlank()) {
        try {
            val jsonArray = JSONArray(productIdsJson)
            for (i in 0 until jsonArray.length()) {
                pIds.add(jsonArray.getString(i))
            }
        } catch (_: Exception) {}
    }
    return TrackedGroup(
        id = id,
        name = name,
        productIds = pIds
    )
}

fun TrackedGroup.toEntity(): TrackedGroupEntity {
    val jsonArray = JSONArray()
    productIds.forEach { jsonArray.put(it) }
    return TrackedGroupEntity(
        id = id,
        name = name,
        productIdsJson = jsonArray.toString()
    )
}
