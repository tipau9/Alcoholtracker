package de.tipau.promille.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserProfileEntity::class,
        DrinkTemplateEntity::class,
        DrinkEntity::class,
        CustomMixEntity::class,
        CrewMemberEntity::class,
        PhotoMemoryEntity::class,
        DayNoteEntity::class,
        PendingSyncOperationEntity::class,
        VomitEventEntity::class,
        MealEventEntity::class,
        BreathalyzerReadingEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun drinkDao(): DrinkDao
    abstract fun drinkTemplateDao(): DrinkTemplateDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun sessionEventDao(): SessionEventDao
    abstract fun crewMemberDao(): CrewMemberDao
    abstract fun dayNoteDao(): DayNoteDao
    abstract fun customMixDao(): CustomMixDao
    abstract fun photoMemoryDao(): PhotoMemoryDao
    abstract fun pendingSyncDao(): PendingSyncDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "promille.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
