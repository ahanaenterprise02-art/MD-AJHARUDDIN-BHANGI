package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [PdfRecordEntity::class], version = 1, exportSchema = false)
abstract class AhaPdfDatabase : RoomDatabase() {
    abstract fun pdfRecordDao(): PdfRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AhaPdfDatabase? = null

        fun getInstance(context: Context): AhaPdfDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AhaPdfDatabase::class.java,
                    "ahapdf_database"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
