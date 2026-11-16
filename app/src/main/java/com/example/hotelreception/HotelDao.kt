package com.example.hotelreception

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// =====================================================================
// DAO (Data Access Object): список запросов к базе.
//  - observe...() возвращают Flow: Room сам присылает новый список после любого изменения таблицы,
//    поэтому экраны обновляются без ручного обновления.
//  - get...() читают данные один раз (нужны внутри транзакции).
//  - upsert...() создают или обновляют записи, delete...() удаляют.
// =====================================================================
@Dao
interface HotelDao {

    // ---------- Чтение: Flow ----------
    @Query("SELECT * FROM rooms ORDER BY number")
    fun observeRooms(): Flow<List<RoomEntity>>

    @Transaction
    @Query("SELECT * FROM reservations ORDER BY id")
    fun observeReservations(): Flow<List<ReservationWithFolio>>

    @Query("SELECT * FROM requests ORDER BY id")
    fun observeRequests(): Flow<List<RequestEntity>>

    @Query("SELECT * FROM shifts ORDER BY id")
    fun observeShifts(): Flow<List<ShiftEntity>>

    // ---------- Чтение: один раз ----------
    @Query("SELECT * FROM rooms ORDER BY number")
    suspend fun getRooms(): List<RoomEntity>

    @Transaction
    @Query("SELECT * FROM reservations ORDER BY id")
    suspend fun getReservations(): List<ReservationWithFolio>

    @Query("SELECT * FROM requests ORDER BY id")
    suspend fun getRequests(): List<RequestEntity>

    @Query("SELECT * FROM shifts ORDER BY id")
    suspend fun getShifts(): List<ShiftEntity>

    @Query("SELECT COUNT(*) FROM rooms")
    suspend fun roomCount(): Int

    // ---------- Создание и обновление ----------
    @Upsert
    suspend fun upsertRooms(items: List<RoomEntity>)

    @Upsert
    suspend fun upsertReservations(items: List<ReservationEntity>)

    @Upsert
    suspend fun upsertFolio(items: List<FolioEntity>)

    @Upsert
    suspend fun upsertRequests(items: List<RequestEntity>)

    @Upsert
    suspend fun upsertShifts(items: List<ShiftEntity>)

    // ---------- Удаление ----------
    @Query("DELETE FROM reservations WHERE id IN (:ids)")
    suspend fun deleteReservations(ids: List<Int>)

    @Query("DELETE FROM folio WHERE id IN (:ids)")
    suspend fun deleteFolio(ids: List<Int>)

    @Query("DELETE FROM requests WHERE id IN (:ids)")
    suspend fun deleteRequests(ids: List<Int>)

    @Query("DELETE FROM shifts WHERE id IN (:ids)")
    suspend fun deleteShifts(ids: List<Int>)

    @Query("DELETE FROM rooms")
    suspend fun clearRooms()

    @Query("DELETE FROM reservations")
    suspend fun clearReservations()

    @Query("DELETE FROM requests")
    suspend fun clearRequests()

    @Query("DELETE FROM shifts")
    suspend fun clearShifts()
}
