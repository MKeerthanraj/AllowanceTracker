package com.kaysyndikayte.allowancetracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DateRangeDto::class, PersonalTransactionDto::class],
    version = 5
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dateRangeDao(): DateRangeDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /**
         * v5 adds the index on transactions.date_range_id.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transactions_date_range_id` " +
                        "ON `transactions` (`date_range_id`)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "allowance_tracker_db"
                )
                    .addMigrations(MIGRATION_4_5)
                    // Kept only as a backstop for the versions below 4 that never had
                    // migrations written; 4 -> 5 goes through MIGRATION_4_5 above.
                    .fallbackToDestructiveMigration(true)
                    .build().also { INSTANCE = it }
            }
    }
}