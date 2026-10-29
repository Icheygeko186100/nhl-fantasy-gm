package com.nhlfantasygm.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 2,
    exportSchema = false
)
abstract class NhlFantasyDatabase : RoomDatabase() {
    abstract fun dao(): NhlFantasyDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE fantasy_teams ADD COLUMN gmDisplayName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE fantasy_teams ADD COLUMN remoteClientId TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile
        private var INSTANCE: NhlFantasyDatabase? = null

        fun getInstance(context: Context): NhlFantasyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    NhlFantasyDatabase::class.java,
                    "nhl_fantasy_gm.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
