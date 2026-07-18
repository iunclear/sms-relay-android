package com.iunclear.smsrelay

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

enum class DeliveryStatus { PENDING, SENT, RETRYING, FAILED }

@Entity(tableName = "relay_messages")
data class RelayMessage(
    @PrimaryKey val messageId: String,
    val sender: String,
    val content: String,
    val receivedAt: Long,
    val status: DeliveryStatus = DeliveryStatus.PENDING,
    val lastError: String? = null
)

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: RelayMessage): Long

    @Query("SELECT * FROM relay_messages WHERE messageId = :messageId")
    suspend fun get(messageId: String): RelayMessage?

    @Query("UPDATE relay_messages SET status = :status, lastError = :error WHERE messageId = :messageId")
    suspend fun updateStatus(messageId: String, status: DeliveryStatus, error: String? = null)

    @Query("SELECT * FROM relay_messages ORDER BY receivedAt DESC LIMIT 30")
    fun recent(): Flow<List<RelayMessage>>

    @Query("DELETE FROM relay_messages")
    suspend fun clear()
}

@Database(entities = [RelayMessage::class], version = 1, exportSchema = false)
abstract class MessageDatabase : RoomDatabase() {
    abstract fun messages(): MessageDao

    companion object {
        @Volatile private var instance: MessageDatabase? = null

        fun get(context: Context): MessageDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                MessageDatabase::class.java,
                "sms_relay.db"
            ).build().also { instance = it }
        }
    }
}
