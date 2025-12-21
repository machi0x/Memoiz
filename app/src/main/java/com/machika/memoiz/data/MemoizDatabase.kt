package com.machika.memoiz.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.machika.memoiz.data.dao.CategoryDao
import com.machika.memoiz.data.dao.MemoDao
import com.machika.memoiz.data.entity.CategoryEntity
import com.machika.memoiz.data.entity.MemoEntity

/**
 * Room database for Memoiz app.
 * Manages categories and memos with proper relationships.
 */
@Database(
    entities = [CategoryEntity::class, MemoEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MemoizDatabase : RoomDatabase() {
    
    abstract fun categoryDao(): CategoryDao
    abstract fun memoDao(): MemoDao
    
    companion object {
        @Volatile
        private var INSTANCE: MemoizDatabase? = null
        
        fun getDatabase(context: Context): MemoizDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MemoizDatabase::class.java,
                    "memoiz_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
