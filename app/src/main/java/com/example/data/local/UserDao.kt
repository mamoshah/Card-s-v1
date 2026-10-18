package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * Data Access Object for single owner authentication.
 */
@Dao
interface UserDao {

    @Query("SELECT * FROM users WHERE LOWER(username) = LOWER(:username) LIMIT 1")
    suspend fun findByHostName(username: String): UserEntity?

    @Query("SELECT * FROM users WHERE LOWER(username) = LOWER(:username) AND password = :password LIMIT 1")
    suspend fun authenticate(username: String, password: String): UserEntity?

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getOwnerAccount(): UserEntity?

    @Query("DELETE FROM users")
    suspend fun clearAllUsers()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity): Long

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("SELECT COUNT(*) FROM users")
    suspend fun countUsers(): Int

    @Query("SELECT * FROM users ORDER BY id ASC")
    suspend fun getAllUsers(): List<UserEntity>
}
