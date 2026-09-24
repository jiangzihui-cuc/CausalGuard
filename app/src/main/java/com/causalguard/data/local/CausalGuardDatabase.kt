package com.causalguard.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        AppProfileEntity::class,
        PrivacyEventEntity::class,
        UsageContextEventEntity::class,
        NetworkEventEntity::class,
        EvidenceLinkEntity::class,
        RiskAssessmentEntity::class,
        RecommendationEntity::class,
        MitigationRecordEntity::class,
        DemoScenarioEntity::class,
        RuleVersionEntity::class,
        AuditLogEntity::class,
    ],
    version = CausalGuardDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CausalGuardDatabase : RoomDatabase() {

    abstract fun privacyEventDao(): PrivacyEventDao

    abstract fun appProfileDao(): AppProfileDao

    abstract fun usageContextDao(): UsageContextDao

    abstract fun networkEventDao(): NetworkEventDao

    abstract fun evidenceLinkDao(): EvidenceLinkDao

    abstract fun riskAssessmentDao(): RiskAssessmentDao

    abstract fun recommendationDao(): RecommendationDao

    abstract fun mitigationDao(): MitigationDao

    abstract fun ruleVersionDao(): RuleVersionDao

    abstract fun demoScenarioDao(): DemoScenarioDao

    abstract fun auditLogDao(): AuditLogDao

    companion object {
        const val VERSION: Int = 1

        private const val NAME: String = "causalguard.db"

        @Volatile
        private var instance: CausalGuardDatabase? = null

        fun get(context: Context): CausalGuardDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): CausalGuardDatabase =
            Room.databaseBuilder(context.applicationContext, CausalGuardDatabase::class.java, NAME)
                .addMigrations(*Migrations.ALL)
                .build()
    }
}
