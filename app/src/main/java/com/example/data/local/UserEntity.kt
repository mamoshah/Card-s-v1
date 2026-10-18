package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a registered user stored in the local Room database.
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "username", index = true)
    val username: String,

    @ColumnInfo(name = "password")
    val password: String,

    @ColumnInfo(name = "display_name")
    val displayName: String = "",

    @ColumnInfo(name = "role")
    val role: String = "User",

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)
