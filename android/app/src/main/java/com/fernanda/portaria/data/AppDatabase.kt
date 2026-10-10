package com.fernanda.portaria.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase

@Dao
interface EventoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun inserir(e: EventoPendente)

    @Query("SELECT * FROM eventos_pendentes WHERE sincronizado = 0 ORDER BY ocorridoEm ASC")
    suspend fun pendentes(): List<EventoPendente>

    @Query("UPDATE eventos_pendentes SET sincronizado = 1 WHERE id = :id")
    suspend fun marcarSincronizado(id: String)
}

@Database(entities = [EventoPendente::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun eventoDao(): EventoDao

    companion object {
        @Volatile private var inst: AppDatabase? = null
        fun get(ctx: Context): AppDatabase =
            inst ?: synchronized(this) {
                inst ?: Room.databaseBuilder(ctx, AppDatabase::class.java, "portaria.db").build()
                    .also { inst = it }
            }
    }
}
