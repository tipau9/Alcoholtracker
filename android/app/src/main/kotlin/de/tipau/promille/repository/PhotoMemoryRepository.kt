package de.tipau.promille.repository

import de.tipau.promille.data.PhotoMemoryDao
import de.tipau.promille.data.PhotoMemoryEntity
import kotlinx.coroutines.flow.Flow

class PhotoMemoryRepository(private val dao: PhotoMemoryDao) {

    val memories: Flow<List<PhotoMemoryEntity>> = dao.getAll()

    suspend fun addMemory(filename: String, bacAtTime: Double?, caption: String? = null): PhotoMemoryEntity {
        val memory = PhotoMemoryEntity(
            id = java.util.UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis() / 1000,
            filename = filename,
            caption = caption,
            bacAtTime = bacAtTime
        )
        dao.insert(memory)
        return memory
    }

    suspend fun deleteMemory(memory: PhotoMemoryEntity) {
        dao.delete(memory)
    }
}
