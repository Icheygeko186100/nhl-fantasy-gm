package com.nhlfantasygm.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        NhlTeamEntity::class,
        PlayerEntity::class,
        PlayerProfileCacheEntity::class,
        FantasyTeamEntity::class,
        TransactionEntity::class,
        DraftSessionEntity::class,
        DraftPickEntity::class,
        TradeProposalEntity::class,
        FantasyMatchupEntity::class,
        SeasonArchiveEntity::class,
        LiveNhlGameEntity::class,
        NewsHeadlineEntity::class,
        SyncStatusEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NhlFantasyDatabase : RoomDatabase() {
    abstract fun dao(): NhlFantasyDao

    companion object {
        @Volatile
        private var INSTANCE: NhlFantasyDatabase? = null

        fun getInstance(context: Context): NhlFantasyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NhlFantasyDatabase::class.java,
                    "nhl_fantasy_gm.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
