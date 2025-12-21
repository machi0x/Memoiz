package com.machi.memoiz.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.machi.memoiz.data.dao.CategoryDao
import com.machi.memoiz.data.dao.MemoDao
import com.machi.memoiz.data.entity.CategoryEntity
import com.machi.memoiz.data.entity.MemoEntity

/**
 * Room database for Memoiz app.
 * Manages categories and memos with proper relationships.
 */
@Database(
    entities = [CategoryEntity::class, MemoEntity::class],
    version = 2,
    exportSchema = false
)
abstract class MemoizDatabase : RoomDatabase() {
    
    abstract fun categoryDao(): CategoryDao
    abstract fun memoDao(): MemoDao
    
    companion object {
        @Volatile
        private var INSTANCE: MemoizDatabase? = null
        
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add subCategory and sourceApp columns to memos table
                database.execSQL("ALTER TABLE memos ADD COLUMN subCategory TEXT")
                database.execSQL("ALTER TABLE memos ADD COLUMN sourceApp TEXT")
            }
        }
        
        fun getDatabase(context: Context): MemoizDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MemoizDatabase::class.java,
                    "memoiz_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
