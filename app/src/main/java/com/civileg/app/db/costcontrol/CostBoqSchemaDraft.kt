package com.civileg.app.db.costcontrol

/** DDL-only unpublished draft. No migration version chosen until production version15 QA is reconciled. */
object CostBoqSchemaDraft {
    val createStatements = listOf(
        """CREATE TABLE IF NOT EXISTS cost_boq_revisions (projectId INTEGER NOT NULL, revisionId TEXT NOT NULL, contentHash TEXT NOT NULL, previousRevisionId TEXT, revisionReason TEXT NOT NULL, currency TEXT, asOfDate TEXT, createdAtMillis INTEGER NOT NULL, PRIMARY KEY(projectId, revisionId), FOREIGN KEY(projectId) REFERENCES projects(id) ON UPDATE NO ACTION ON DELETE CASCADE)""",
        """CREATE UNIQUE INDEX IF NOT EXISTS index_cost_boq_revisions_projectId_revisionId_contentHash ON cost_boq_revisions(projectId, revisionId, contentHash)""",
        """CREATE TABLE IF NOT EXISTS cost_boq_items (projectId INTEGER NOT NULL, revisionId TEXT NOT NULL, contentHash TEXT NOT NULL, itemId TEXT NOT NULL, sourceWorkbookHash TEXT, sourceSheet TEXT, sourceCell TEXT, displayedItemNumber TEXT NOT NULL, originalDescription TEXT NOT NULL, originalUnit TEXT NOT NULL, quantityText TEXT, quantityOrigin TEXT NOT NULL, quantityEvidencePayload TEXT NOT NULL, quantityReviewConfirmed INTEGER NOT NULL, quotedRateText TEXT, currency TEXT, priceAsOfDate TEXT, priceSource TEXT NOT NULL, payloadVersion INTEGER NOT NULL, analysisPayload TEXT, PRIMARY KEY(projectId, revisionId, itemId), FOREIGN KEY(projectId, revisionId, contentHash) REFERENCES cost_boq_revisions(projectId, revisionId, contentHash) ON UPDATE NO ACTION ON DELETE CASCADE)""",
        """CREATE INDEX IF NOT EXISTS index_cost_boq_items_projectId_revisionId_contentHash ON cost_boq_items(projectId, revisionId, contentHash)""",
        """CREATE TABLE IF NOT EXISTS cost_boq_heads (projectId INTEGER NOT NULL, revisionId TEXT NOT NULL, contentHash TEXT NOT NULL, PRIMARY KEY(projectId), FOREIGN KEY(projectId, revisionId, contentHash) REFERENCES cost_boq_revisions(projectId, revisionId, contentHash) ON UPDATE NO ACTION ON DELETE CASCADE)""",
        """CREATE INDEX IF NOT EXISTS index_cost_boq_heads_projectId_revisionId_contentHash ON cost_boq_heads(projectId, revisionId, contentHash)"""
    )
}
