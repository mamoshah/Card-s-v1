package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * App Room Database holding the single owner account for two-step server authentication.
 */
@Database(
    entities = [UserEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao

    companion object {
        const val OWNER_HOST_NAME = "MtshoHunt@server.com"
        const val OWNER_PASSWORD = "Mesho219&212&410"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope = CoroutineScope(Dispatchers.IO)): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "card_tracker_auth.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .addCallback(DatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        ensureOnlyOwnerAccountExists(database.userDao())
                    }
                }
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        ensureOnlyOwnerAccountExists(database.userDao())
                    }
                }
            }

            suspend fun ensureOnlyOwnerAccountExists(userDao: UserDao) {
                // Ensure all old demo/test accounts are removed and only single owner account exists
                userDao.clearAllUsers()
                userDao.insertUser(
                    UserEntity(
                        id = 1,
                        username = OWNER_HOST_NAME,
                        password = OWNER_PASSWORD,
                        displayName = "Mtsho Hunt",
                        role = "Owner",
                        createdAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }
}
