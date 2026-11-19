package com.example.homeinvasion.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

interface MotionRepository {
    fun observeRecent(homeId: String): kotlinx.coroutines.flow.Flow<EventFeed>
    suspend fun getEvent(homeId: String, eventId: String): MotionEvent?
    suspend fun loadSnapshot(homeId: String, event: MotionEvent): Bitmap
}

@Singleton
class FirebaseMotionRepository @Inject constructor(private val client: FirebaseClient) : MotionRepository {
    override fun observeRecent(homeId: String) = callbackFlow {
        val database = client.database
        if (database == null) {
            trySend(EventFeed(error = AuthRepository.FIREBASE_DATABASE_SETUP_MESSAGE))
            close()
            return@callbackFlow
        }

        val query = database.getReference("homes/$homeId/events")
            .orderByChild("detectedAt")
            .limitToLast(20)
        val connection = database.getReference(".info/connected")
        var events = emptyList<MotionEvent>()
        var online = false
        var error: String? = null
        fun publish() = trySend(EventFeed(events, online, error))
        val eventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                events = snapshot.children.mapNotNull { child ->
                    child.getValue(EventRecord::class.java)?.takeIf { it.detectedAt > 0 && it.snapshotPath.isNotBlank() }
                        ?.let { MotionEvent(child.key.orEmpty(), it.detectedAt, it.snapshotPath) }
                }.sortedByDescending(MotionEvent::detectedAt)
                error = null
                publish()
            }
            override fun onCancelled(reason: DatabaseError) {
                error = reason.message.ifBlank { "Couldn't load motion events." }
                publish()
            }
        }
        val connectionListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) { online = snapshot.getValue(Boolean::class.java) == true; publish() }
            override fun onCancelled(reason: DatabaseError) { online = false; publish() }
        }
        query.addValueEventListener(eventListener)
        connection.addValueEventListener(connectionListener)
        awaitClose {
            query.removeEventListener(eventListener)
            connection.removeEventListener(connectionListener)
        }
    }.catch { emit(EventFeed(error = it.message ?: "Couldn't load motion events.")) }

    override suspend fun getEvent(homeId: String, eventId: String): MotionEvent? {
        val database = client.database ?: error(AuthRepository.FIREBASE_DATABASE_SETUP_MESSAGE)
        val snapshot = database.getReference("homes/$homeId/events/$eventId").get().await()
        val record = snapshot.getValue(EventRecord::class.java) ?: return null
        return MotionEvent(eventId, record.detectedAt, record.snapshotPath)
    }

    override suspend fun loadSnapshot(homeId: String, event: MotionEvent): Bitmap = withContext(Dispatchers.IO) {
        val expectedPath = "homes/$homeId/events/${event.id}.jpg"
        require(event.snapshotPath == expectedPath) { "The snapshot reference is invalid." }
        val storage = client.storage ?: error(AuthRepository.FIREBASE_SETUP_MESSAGE)
        val bytes = storage.reference.child(event.snapshotPath).getBytes(MAX_SNAPSHOT_BYTES).await()
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("The stored snapshot is not a valid JPEG image.")
    }

    companion object { private const val MAX_SNAPSHOT_BYTES = 5L * 1024 * 1024 }
}
