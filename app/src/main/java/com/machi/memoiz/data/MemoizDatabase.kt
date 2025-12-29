package com.machi.memoiz.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.machi.memoiz.data.dao.MemoDao
import com.machi.memoiz.data.entity.MemoEntity

@Database(
    entities = [MemoEntity::class],
    version = 4, // Incremented version to 4
    exportSchema = false
)
abstract class MemoizDatabase : RoomDatabase() {

    abstract fun memoDao(): MemoDao

    companion object {
        @Volatile
        private var INSTANCE: MemoizDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE memos ADD COLUMN subCategory TEXT")
                database.execSQL("ALTER TABLE memos ADD COLUMN sourceApp TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE categories ADD COLUMN name_en TEXT")
                database.execSQL("ALTER TABLE categories ADD COLUMN name_ja TEXT")
            }
        }

        // Migration to remove categories table and update memos table
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. Create a new temporary table with the desired schema
                database.execSQL("""
                    CREATE TABLE memos_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        content TEXT NOT NULL,
                        imageUri TEXT,
                        category TEXT NOT NULL,
                        subCategory TEXT,
                        summary TEXT,
                        sourceApp TEXT,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // 2. Copy data from the old memos and categories tables into the new one
                database.execSQL("""
                    INSERT INTO memos_new (id, content, imageUri, subCategory, sourceApp, createdAt, category)
                    SELECT m.id, m.content, m.imageUri, m.subCategory, m.sourceApp, m.createdAt, COALESCE(c.name, 'Uncategorized')
                    FROM memos AS m
                    LEFT JOIN categories AS c ON m.categoryId = c.id
                """.trimIndent())

                // 3. Drop the old tables
                database.execSQL("DROP TABLE memos")
                database.execSQL("DROP TABLE categories")

                // 4. Rename the new table to the original table name
                database.execSQL("ALTER TABLE memos_new RENAME TO memos")
            }
        }

        fun getDatabase(context: Context): MemoizDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MemoizDatabase::class.java,
                    "memoiz_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
