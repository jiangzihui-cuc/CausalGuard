package com.causalguard.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 数据库迁移登记处（docs/08-database-schema.sql）。
 *
 * 规则：每次修改 schema 必须递增 [CausalGuardDatabase.VERSION] 并在此新增一个
 * `Migration(from, to)`，同时在 `app/schemas/` 保留导出的 schema JSON 供迁移测试比对。
 * v1 为初始建库，没有历史迁移。
 */
object Migrations {
    /** v1 → v2：RiskAssessment 按 docs/10 §2 补 `category`、`matchedRules`。 */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE risk_assessment ADD COLUMN category TEXT NOT NULL DEFAULT 'unknown'")
            db.execSQL("ALTER TABLE risk_assessment ADD COLUMN matchedRules TEXT NOT NULL DEFAULT '[]'")
        }
    }

    /**
     * v2 → v3：MitigationRecord 补 `executionStatus`，区分“已执行”与“失败/不可用”，
     * 避免把失败持久化成已执行（PR #18 review 修正）。
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE mitigation_record ADD COLUMN executionStatus TEXT NOT NULL DEFAULT 'unknown'")
        }
    }

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
}
