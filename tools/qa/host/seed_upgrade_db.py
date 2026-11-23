#!/usr/bin/env python3
"""Disposable synthetic-upgrade QA: seed sentinel rows into the pulled OLD database.

Runs on the CI runner host, never on a user device. Input is the coherent pulled
SQLite main file (any WAL was checkpointed by the caller's pull step). It refuses
to run unless the actual user_version is exactly 14: a different version means the
real migration chain differs and must be planned, not fabricated.

Cases:
  fresh-14       The pulled DB is used as-is (standard v14 schema).
  historical-8to9-derived
                 The designs table is first rebuilt into the historical 8->9-derived
                 shape (nullable createdAt), preserving every row, index and the id
                 sequence. This is a MODIFIED HISTORICAL-SHAPE FIXTURE: it is not a
                 valid fresh-14 schema and not a real executed 8->9 upgrade.

Sentinels (both cases): one sentinel project; four sentinel designs (dated BEAM,
unknown-date BEAM, sequence-advancing BEAM, representative saved FRAME_ANALYSIS
record in the app's real schemaVersion-2 shape); one valid and one orphan-null
site_inspections link. In the historical case the unknown-date design stores
createdAt NULL and must read back as 0 after the 14->15 repair.
Writes qa_expectations.json for UpgradePreservationTest.
"""
import argparse
import json
import sqlite3
import sys

SENTINEL_PROJECT_ID = 900001
SENTINEL_PROJECT_NAME = "QA_SENTINEL_PROJECT_DO_NOT_SHIP"


def _compact(obj):
    return json.dumps(obj, separators=(",", ":"), ensure_ascii=False)


# Real saved-frame record shape, grounded in source head 78cdca0:
# DesignRepository.saveFrameAnalysisDesign writes inputData as schemaVersion 2 with
# nodes/members/nodalLoads/memberLoads/slabs/dimensions as NESTED JSON STRINGS plus
# steelFy; results is gson.toJson(FrameAnalysisResult). Field names match
# FrameEntities.kt exactly (this is what loadFrameDesign reads back).
_FRAME_NODES = [
    {"id": 1, "x": 0.0, "y": 0.0, "support": "Fixed"},
    {"id": 2, "x": 6.0, "y": 0.0, "support": "Fixed"},
    {"id": 3, "x": 0.0, "y": 3.5, "support": "Free"},
    {"id": 4, "x": 6.0, "y": 3.5, "support": "Free"},
]
_SECTION = {"width": 300.0, "depth": 600.0, "fcu": 25.0, "fy": 400.0, "cover": 50.0}
_FRAME_MEMBERS = [
    {"id": 1, "nodeI": 1, "nodeJ": 3, "materialType": "Concrete", "memberType": "Column",
     "concreteSection": _SECTION, "steelSectionName": None, "name": "C1", "releaseI": False, "releaseJ": False},
    {"id": 2, "nodeI": 2, "nodeJ": 4, "materialType": "Concrete", "memberType": "Column",
     "concreteSection": _SECTION, "steelSectionName": None, "name": "C2", "releaseI": False, "releaseJ": False},
    {"id": 3, "nodeI": 3, "nodeJ": 4, "materialType": "Concrete", "memberType": "Beam",
     "concreteSection": _SECTION, "steelSectionName": None, "name": "B1", "releaseI": False, "releaseJ": False},
]
_FRAME_MEMBER_LOADS = [
    {"memberId": 3, "loadType": "UDL", "value": 25.0, "position": 0.0, "loadCase": "DL"},
]
FRAME_INPUT = _compact({
    "schemaVersion": 2,
    "nodes": _compact(_FRAME_NODES),
    "members": _compact(_FRAME_MEMBERS),
    "nodalLoads": "[]",
    "memberLoads": _compact(_FRAME_MEMBER_LOADS),
    "slabs": "[]",
    "dimensions": "[]",
    "steelFy": 355.0,
})
FRAME_RESULT = _compact({
    "nodeResults": [], "memberEndForces": [], "memberDiagrams": [],
    "concreteDesignResults": [], "steelDesignResults": [], "isSolved": False,
    "equilibriumFx": 0.0, "equilibriumFy": 0.0, "equilibriumMz": 0.0, "loadEnvelopes": [],
})

DESIGNS = [
    # (id, name, type, input, results, created_at_epoch_ms_or_None, expected_after_upgrade)
    (900101, "QA_SENTINEL_DESIGN_DATED", "BEAM", '{"qa":true}', '{"qa":true}', 1609459200000, 1609459200000),
    (900102, "QA_SENTINEL_DESIGN_UNKNOWNDATE", "BEAM", '{"qa":true}', '{"qa":true}', None, 0),
    (900120, "QA_SENTINEL_DESIGN_SEQ_ADV", "BEAM", '{"qa":true}', '{"qa":true}', 1612137600000, 1612137600000),
    (900121, "QA_SENTINEL_FRAME_RECORD", "FRAME_ANALYSIS", FRAME_INPUT, FRAME_RESULT, 1609459200000, 1609459200000),
]
ADVANCED_SEQ = 900121  # simulates ids in between having been deleted long ago

DESIGN_COLS_V14 = (
    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, projectId INTEGER NOT NULL, "
    "type TEXT NOT NULL, name TEXT NOT NULL, inputData TEXT NOT NULL, results TEXT NOT NULL, "
    "isSafe INTEGER NOT NULL, utilizationRatio REAL NOT NULL DEFAULT 0.0, codeUsed TEXT NOT NULL, "
    "concreteVolume REAL NOT NULL DEFAULT 0.0, steelWeight REAL NOT NULL DEFAULT 0.0, "
    "totalCost REAL NOT NULL DEFAULT 0.0, createdAt INTEGER"
)


def fail(msg):
    sys.exit("seed_upgrade_db: " + msg)

def set_designs_seq(con, seq, context):
    """Normalize sqlite_sequence for 'designs' to exactly ONE row whose seq
    preserves the old maximum (max of existing rows and the requested value).
    A blind INSERT after the historical ALTER-rename can leave TWO 'designs'
    rows (observed on the historical shape: old sequence 50 yielded rows 1 and
    50), making AUTOINCREMENT behavior ambiguous. Update-existing /
    insert-if-absent, then assert a single row. Fixture defect, not a
    production diagnosis."""
    rows = con.execute("SELECT seq FROM sqlite_sequence WHERE name = 'designs'").fetchall()
    keep = max([r[0] for r in rows] + [seq])
    if rows:
        con.execute("DELETE FROM sqlite_sequence WHERE name = 'designs'")
    con.execute("INSERT INTO sqlite_sequence (name, seq) VALUES ('designs', ?)", (keep,))
    cnt, mx = con.execute(
        "SELECT COUNT(*), MAX(seq) FROM sqlite_sequence WHERE name = 'designs'").fetchone()
    if cnt != 1 or mx < seq:
        fail(f"{context}: sqlite_sequence for designs not normalized "
             f"(rows={cnt}, max={mx}, wanted >= {seq})")


def rebuild_designs_nullable_created_at(con):
    """Rebuild designs in the historical 8->9-derived shape, preserving rows,
    the projectId index and the id sequence. Explicitly a fixture modification."""
    seq = con.execute("SELECT seq FROM sqlite_sequence WHERE name = 'designs'").fetchone()
    seq = seq[0] if seq else None
    con.execute("PRAGMA foreign_keys = OFF")
    con.execute("BEGIN IMMEDIATE")
    con.execute(f"CREATE TABLE designs_historical ({DESIGN_COLS_V14}, "
                "FOREIGN KEY(projectId) REFERENCES projects(id) ON DELETE CASCADE)")
    con.execute(
        "INSERT INTO designs_historical SELECT id, projectId, type, name, inputData, results, "
        "isSafe, utilizationRatio, codeUsed, concreteVolume, steelWeight, totalCost, createdAt FROM designs"
    )
    con.execute("DROP TABLE designs")
    con.execute("ALTER TABLE designs_historical RENAME TO designs")
    con.execute("CREATE INDEX IF NOT EXISTS index_designs_projectId ON designs(projectId)")
    if seq is not None:
        set_designs_seq(con, seq, "historical rebuild")
    con.execute("COMMIT")
    con.execute("PRAGMA foreign_keys = OFF")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", required=True, help="pulled civil_eg_database main file")
    ap.add_argument("--expectations", required=True, help="output qa_expectations.json")
    ap.add_argument("--case", required=True, choices=["fresh-14", "historical-8to9-derived"])
    args = ap.parse_args()

    con = sqlite3.connect(args.db)
    con.isolation_level = None
    con.execute("PRAGMA busy_timeout = 5000")
    mode = str(con.execute("PRAGMA journal_mode").fetchone()[0]).lower()
    if mode == "wal":
        con.execute("PRAGMA wal_checkpoint(TRUNCATE)")

    version = con.execute("PRAGMA user_version").fetchone()[0]
    if version != 14:
        fail(f"actual user_version is {version}, expected 14; plan the real chain instead")

    identity = con.execute("SELECT identity_hash FROM room_master_table WHERE id = 42").fetchone()
    if identity is None:
        fail("room_master_table identity hash row missing; not a Room database in the expected shape")
    identity_hash = identity[0]

    if args.case == "historical-8to9-derived":
        rebuild_designs_nullable_created_at(con)

    con.execute("BEGIN IMMEDIATE")
    project_count = con.execute("SELECT COUNT(*) FROM projects").fetchone()[0]
    con.execute(
        "INSERT OR REPLACE INTO projects (id, name, location, clientName, description, status, code, createdAt, updatedAt)"
        " VALUES (?, ?, '', '', 'synthetic sentinel', 'ACTIVE', 'ECP', 1609459200000, 1609459200000)",
        (SENTINEL_PROJECT_ID, SENTINEL_PROJECT_NAME),
    )
    historical = args.case == "historical-8to9-derived"
    for did, name, dtype, inp, res, created, _after in DESIGNS:
        # fresh-14 schema has createdAt NOT NULL: the unknown-date sentinel is the
        # explicit 0 ("date unknown") value there; only the historical-shape fixture
        # may store NULL, which the 14->15 repair must normalize to 0.
        if created is None and not historical:
            created = 0
        con.execute(
            "INSERT OR REPLACE INTO designs (id, projectId, type, name, inputData, results, isSafe,"
            " utilizationRatio, codeUsed, concreteVolume, steelWeight, totalCost, createdAt)"
            " VALUES (?, ?, ?, ?, ?, ?, 1, 0.5, 'ECP', 1.0, 2.0, 3.0, ?)",
            (did, SENTINEL_PROJECT_ID, dtype, name, inp, res, created),
        )
    links = []
    has_inspections = con.execute(
        "SELECT 1 FROM sqlite_master WHERE type='table' AND name='site_inspections'"
    ).fetchone()
    if has_inspections and "designId" in [r[1] for r in con.execute("PRAGMA table_info(site_inspections)")]:
        notnull_defaults = {
            "projectId": SENTINEL_PROJECT_ID,
            "inspectorName": "'QA_SENTINEL'",
            "date": 1609459200000,
            "formworkSafe": 1,
            "rebarMatchesDesign": 1,
            "coverAdequate": 1,
            "cleanlinessPassed": 1,
            "comments": "'synthetic sentinel'",
        }
        for iid, design_id in ((900201, DESIGNS[0][0]), (900202, None)):
            names, values = ["id", "designId"], [str(iid), "NULL" if design_id is None else str(design_id)]
            for col, notnull in ((r[1], r[3]) for r in con.execute("PRAGMA table_info(site_inspections)")):
                if col in names or not notnull:
                    continue
                if col not in notnull_defaults:
                    fail(f"site_inspections has unexpected NOT NULL column {col}; extend the seeder deliberately")
                names.append(col)
                values.append(str(notnull_defaults[col]))
            con.execute(f"INSERT OR REPLACE INTO site_inspections ({', '.join(names)}) VALUES ({', '.join(values)})")
        links = [
            {"inspectionId": 900201, "designId": DESIGNS[0][0]},
            {"inspectionId": 900202, "designId": None},
        ]
    set_designs_seq(con, ADVANCED_SEQ, "sentinel sequence advance")
    seeded_seq = con.execute("SELECT seq FROM sqlite_sequence WHERE name = 'designs'").fetchone()[0]
    con.execute("COMMIT")
    con.close()

    exp = {
        "case": "fresh-old-14" if args.case == "fresh-14" else "historical-8to9-derived-modified-fixture",
        "identityHashBefore": identity_hash,
        "seededProjectCount": project_count + 1,
        "sentinelProject": {"id": SENTINEL_PROJECT_ID, "name": SENTINEL_PROJECT_NAME},
        "sentinelDesigns": [
            {"id": did, "name": name, "type": dtype, "inputData": inp, "results": res,
             "codeUsed": "ECP", "createdAtAfterUpgrade": after}
            for did, name, dtype, inp, res, _created, after in DESIGNS
        ],
        "inspectionLinks": links,
        "designSeqAfterSeeding": seeded_seq,
    }
    with open(args.expectations, "w") as f:
        json.dump(exp, f, indent=2)
    print(f"seeded {exp['case']} case; identity hash recorded; expectations written")


if __name__ == "__main__":
    main()
