package de.tipau.promille.repository

import de.tipau.promille.data.CrewMemberDao
import de.tipau.promille.data.CrewMemberEntity
import kotlinx.coroutines.flow.Flow

class CrewRepository(private val dao: CrewMemberDao) {

    val members: Flow<List<CrewMemberEntity>> = dao.getAll()

    suspend fun countNonSelf(): Int = dao.countNonSelf()

    suspend fun insertOrUpdate(member: CrewMemberEntity) {
        dao.insertOrUpdate(member)
    }

    suspend fun update(member: CrewMemberEntity) {
        dao.update(member)
    }

    suspend fun delete(member: CrewMemberEntity) {
        dao.delete(member)
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }
}
