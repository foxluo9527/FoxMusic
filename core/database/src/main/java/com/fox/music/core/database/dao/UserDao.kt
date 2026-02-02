package com.fox.music.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.fox.music.core.database.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Query("SELECT * FROM users WHERE isCurrentUser = 1 LIMIT 1")
    fun getCurrentUser(): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE isCurrentUser = 1 LIMIT 1")
    suspend fun getCurrentUserOnce(): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserById(id: Long): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id")
    fun observeUserById(id: Long): Flow<UserEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Upsert
    suspend fun upsertUser(user: UserEntity)

    @Query("UPDATE users SET isCurrentUser = 0")
    suspend fun clearCurrentUser()

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteUserById(id: Long)

    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()
}
