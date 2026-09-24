package com.causalguard.data.local

import androidx.room.migration.Migration

/**
 * 数据库迁移登记处（docs/08-database-schema.sql）。
 *
 * 规则：每次修改 schema 必须递增 [CausalGuardDatabase.VERSION] 并在此新增一个
 * `Migration(from, to)`，同时在 `app/schemas/` 保留导出的 schema JSON 供迁移测试比对。
 * 首个版本（v1）为初始建库，没有历史迁移。
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
