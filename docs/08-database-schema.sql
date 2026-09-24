-- 隐私因果哨兵 数据库表结构 v0.1
-- 目标数据库：SQLite / Room
-- 最后更新：2026-09-24（阶段 2 设计冻结；Room 实现延后到阶段 3）
-- 说明：字段与 docs/07-data-model.md 一一对应；Room Entity 类名与表名映射见注释。
-- 契约版本：schema_version 与 docs/09-event-contract.md 的 schemaVersion 对齐。
-- 注意：不保存剪贴板/通讯录/精确位置原文。

PRAGMA foreign_keys = ON;

-- AppProfile: Room Entity AppProfileEntity
CREATE TABLE IF NOT EXISTS app_profile (
    packageName         TEXT    NOT NULL PRIMARY KEY,
    appName             TEXT    NOT NULL,
    uid                 INTEGER NOT NULL DEFAULT -1,
    versionName         TEXT,
    versionCode         INTEGER,
    declaredPermissions TEXT,               -- JSON array
    grantedPermissions  TEXT,               -- JSON array
    sceneType           TEXT    NOT NULL DEFAULT 'unknown',
    isSystemApp         INTEGER NOT NULL DEFAULT 0,
    updatedAt           INTEGER NOT NULL
);

-- PrivacyEvent: 统一事件
CREATE TABLE IF NOT EXISTS privacy_event (
    schemaVersion    TEXT    NOT NULL DEFAULT '0.1',
    eventId          TEXT    NOT NULL PRIMARY KEY,
    appId            TEXT    NOT NULL DEFAULT 'unknown',
    appName          TEXT,
    eventType        TEXT    NOT NULL,       -- clipboard|location|contacts|network|usage_context|permission
    timestamp        INTEGER NOT NULL,
    foregroundState  TEXT    NOT NULL DEFAULT 'unknown',
    source           TEXT    NOT NULL,       -- system_api|usage_stats|vpn|demo|mock
    evidenceLevel    TEXT    NOT NULL DEFAULT 'E5',
    evidenceSummary  TEXT,
    category         TEXT    NOT NULL DEFAULT 'unknown',
    riskScore        INTEGER NOT NULL DEFAULT 0,
    confidence       TEXT    NOT NULL DEFAULT 'low',
    explanation      TEXT,
    recommendationId TEXT,
    isDemo           INTEGER NOT NULL DEFAULT 0,
    dedupKey         TEXT,
    createdAt        INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_event_app_time   ON privacy_event(appId, timestamp);
CREATE INDEX IF NOT EXISTS idx_event_type_time  ON privacy_event(eventType, timestamp);
CREATE INDEX IF NOT EXISTS idx_event_dedup      ON privacy_event(dedupKey);
CREATE INDEX IF NOT EXISTS idx_event_demo       ON privacy_event(isDemo);

-- UsageContextEvent
CREATE TABLE IF NOT EXISTS usage_context_event (
    id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    packageName TEXT    NOT NULL,
    timestamp   INTEGER NOT NULL,
    state       TEXT    NOT NULL,            -- foreground|background
    screenOn    INTEGER NOT NULL DEFAULT 0,
    source      TEXT    NOT NULL DEFAULT 'usage_stats'
);

CREATE INDEX IF NOT EXISTS idx_usage_pkg_time ON usage_context_event(packageName, timestamp);

-- NetworkEvent
CREATE TABLE IF NOT EXISTS network_event (
    id          INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    eventId     TEXT    NOT NULL,
    packageName TEXT    NOT NULL DEFAULT 'unknown',
    uid         INTEGER NOT NULL DEFAULT -1,
    protocol    TEXT,
    remoteIp    TEXT,
    remotePort  INTEGER,
    domainHint  TEXT,
    bytesIn     INTEGER NOT NULL DEFAULT 0,
    bytesOut    INTEGER NOT NULL DEFAULT 0,
    timestamp   INTEGER NOT NULL,
    blocked     INTEGER NOT NULL DEFAULT 0,
    source      TEXT    NOT NULL DEFAULT 'vpn',
    FOREIGN KEY (eventId) REFERENCES privacy_event(eventId) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_network_event   ON network_event(eventId);
CREATE INDEX IF NOT EXISTS idx_network_app_time ON network_event(packageName, timestamp);

-- EvidenceLink
CREATE TABLE IF NOT EXISTS evidence_link (
    id            INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    eventId       TEXT    NOT NULL,
    linkedEventId TEXT,
    relation      TEXT    NOT NULL,          -- temporal|app|rule
    evidenceLevel TEXT    NOT NULL DEFAULT 'E5',
    description   TEXT,
    ruleId        TEXT,
    createdAt     INTEGER NOT NULL,
    FOREIGN KEY (eventId) REFERENCES privacy_event(eventId) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_evidence_event ON evidence_link(eventId);

-- RiskAssessment
CREATE TABLE IF NOT EXISTS risk_assessment (
    id                TEXT    NOT NULL PRIMARY KEY,
    eventId           TEXT    NOT NULL,
    ruleVersion       TEXT    NOT NULL,
    riskScore         INTEGER NOT NULL DEFAULT 0,
    riskLevel         TEXT    NOT NULL DEFAULT 'low',
    scenarioMatch     TEXT    NOT NULL DEFAULT 'unknown',
    confidence        TEXT    NOT NULL DEFAULT 'low',
    explanationBoundary TEXT,
    evidenceIds       TEXT,                  -- JSON array
    createdAt         INTEGER NOT NULL,
    FOREIGN KEY (eventId) REFERENCES privacy_event(eventId) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_risk_event ON risk_assessment(eventId);

-- Recommendation
CREATE TABLE IF NOT EXISTS recommendation (
    recommendationId TEXT    NOT NULL PRIMARY KEY,
    riskType         TEXT    NOT NULL,
    title            TEXT    NOT NULL,
    reason           TEXT,
    systemPath       TEXT,
    expectedImpact   TEXT,
    reversible       INTEGER NOT NULL DEFAULT 1,
    applicableVersion TEXT,
    evidenceIds      TEXT                    -- JSON array
);

-- MitigationRecord
CREATE TABLE IF NOT EXISTS mitigation_record (
    id               INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    packageName      TEXT    NOT NULL,
    recommendationId TEXT,
    action           TEXT    NOT NULL,       -- block_domain|block_app|open_settings
    target           TEXT,
    executedAt       INTEGER NOT NULL,
    ruleVersion      TEXT,
    preSnapshot      TEXT,                   -- JSON
    postResult       TEXT    NOT NULL DEFAULT 'unknown', -- reduced|no_change|unknown
    observationEnd   INTEGER,
    reviewNotes      TEXT
);

CREATE INDEX IF NOT EXISTS idx_mitigation_pkg ON mitigation_record(packageName, executedAt);

-- DemoScenario
CREATE TABLE IF NOT EXISTS demo_scenario (
    id             TEXT    NOT NULL PRIMARY KEY,
    title          TEXT    NOT NULL,
    description    TEXT,
    groundTruth    TEXT,                     -- JSON
    expectedOutput TEXT,                     -- JSON
    lastRunAt      INTEGER
);

-- RuleVersion
CREATE TABLE IF NOT EXISTS rule_version (
    ruleVersion  TEXT    NOT NULL PRIMARY KEY,
    description  TEXT,
    publishedAt  INTEGER NOT NULL,
    ruleCount    INTEGER NOT NULL DEFAULT 0
);

-- AuditLog
CREATE TABLE IF NOT EXISTS audit_log (
    id           INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    type         TEXT    NOT NULL,           -- ai_call|config_change|consent
    modelName    TEXT,
    inputFields  TEXT,                       -- JSON 白名单字段
    outputStatus TEXT,                       -- success|fallback|error
    createdAt    INTEGER NOT NULL
);
