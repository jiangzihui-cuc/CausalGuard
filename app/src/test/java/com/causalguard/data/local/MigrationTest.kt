package com.causalguard.data.local

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 数据库迁移回归测试（docs/08-database-schema.sql）。
 *
 * 验证 v2 → v3（[Migrations.MIGRATION_2_3]）：既有 `mitigation_record` 记录被保留，
 * 且新增 `executionStatus` 列以 `unknown` 作为默认值——历史记录不伪装成已执行。
 * 通过真实的 [Migrations.MIGRATION_2_3] 迁移（onUpgrade 路径）驱动，而非仅新建 v3 DB。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private val dbName = "migration-test.db"

    /** v2 schema 的 `mitigation_record`（无 `executionStatus` 列），与 `app/schemas/.../2.json` 一致。 */
    private val createMitigationRecordV2 = """
        CREATE TABLE IF NOT EXISTS `mitigation_record` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `packageName` TEXT NOT NULL,
            `recommendationId` TEXT,
            `action` TEXT NOT NULL,
            `target` TEXT,
            `executedAt` INTEGER NOT NULL,
            `ruleVersion` TEXT,
            `preSnapshot` TEXT,
            `postResult` TEXT NOT NULL DEFAULT 'unknown',
            `observationEnd` INTEGER,
            `reviewNotes` TEXT
        )
    """.trimIndent()

    @Test
    fun migrate2To3_preservesMitigationRecordAndDefaultsExecutionStatusToUnknown() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(dbName)

        openAtVersion(context, 2).use { v2 ->
            v2.execSQL(
                "INSERT INTO mitigation_record " +
                    "(packageName, recommendationId, action, target, executedAt, ruleVersion, preSnapshot, postResult, observationEnd, reviewNotes) " +
                    "VALUES ('com.demo.calculator', 'rec-1', 'BLOCK_DOMAIN', 'example.com', 1789920303000, " +
                    "'rules-v0.1', '{\"presence\":\"NO_REQUEST\"}', 'unknown', 1789920603000, NULL)",
            )
        }

        openAtVersion(context, 3).use { db ->
            db.query("SELECT * FROM mitigation_record").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.count)
                assertEquals("com.demo.calculator", cursor.getString(cursor.getColumnIndexOrThrow("packageName")))
                assertEquals("BLOCK_DOMAIN", cursor.getString(cursor.getColumnIndexOrThrow("action")))
                assertEquals("example.com", cursor.getString(cursor.getColumnIndexOrThrow("target")))
                assertEquals("unknown", cursor.getString(cursor.getColumnIndexOrThrow("executionStatus")))
            }

            db.query("PRAGMA table_info(mitigation_record)").use { cursor ->
                var found = false
                var defaultValue: String? = null
                while (cursor.moveToNext()) {
                    if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "executionStatus") {
                        found = true
                        defaultValue = cursor.getString(cursor.getColumnIndexOrThrow("dflt_value"))
                    }
                }
                assertTrue("executionStatus column missing after migration", found)
                assertNotNull(defaultValue)
                assertEquals("'unknown'", defaultValue)
            }
        }

        context.deleteDatabase(dbName)
    }

    private fun openAtVersion(context: Context, version: Int): SupportSQLiteDatabase {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(createMitigationRecordV2)
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    if (oldVersion == 2 && newVersion == 3) {
                        Migrations.MIGRATION_2_3.migrate(db)
                    }
                }
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }
}
