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

    val ALL: Array<Migration> = arrayOf(MIGRATION_1_2)
}
