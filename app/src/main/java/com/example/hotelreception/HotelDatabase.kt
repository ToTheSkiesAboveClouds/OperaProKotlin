package com.example.hotelreception

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

// =====================================================================
// БАЗА ДАННЫХ Room. Файл базы "hotel.db" лежит в памяти приложения и сохраняется
// между запусками. Все таблицы перечислены в entities, запросы — в HotelDao.
// =====================================================================
@Database(
    entities = [
        RoomEntity::class,
        ReservationEntity::class,
        FolioEntity::class,
        RequestEntity::class,
        ShiftEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class HotelDatabase : RoomDatabase() {

    abstract fun dao(): HotelDao

    companion object {
        fun create(context: Context): HotelDatabase =
            Room.databaseBuilder(context.applicationContext, HotelDatabase::class.java, "hotel.db")
                // Учебный проект: если схема изменится, база пересоздаётся (миграции не пишем)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
