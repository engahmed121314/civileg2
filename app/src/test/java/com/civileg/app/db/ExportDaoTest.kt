package com.civileg.app.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExportDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ExportDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.exportDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertExport_andGetForProject_roundTrips() = runBlocking {
        // إنشاء مشروع أولًا
        val projectId = db.projectDao().insertProject(
            Project(
                name = "مشروع تجريبي",
                location = "القاهرة",
                clientName = "عميل تجريبي",
                code = "ECP"
            )
        )

        // إدخال تصدير PDF (تاريخ أقدم) — التواريخ صريحة حتى لا يعتمد ترتيب النتائج على التوقيت الفعلي
        val pdfId = dao.insertExport(
            ExportEntity(
                projectId = projectId,
                elementType = "كمرة",
                designCode = "ECP",
                exportType = "PDF",
                filePath = "/storage/emulated/0/Download/Beam_ECP.pdf",
                fileSizeBytes = 152000,
                createdAt = Date(1_700_000_000_000L)
            )
        )

        // إدخال تصدير DXF لنفس المشروع (تاريخ أحدث)
        val dxfId = dao.insertExport(
            ExportEntity(
                projectId = projectId,
                elementType = "عمود",
                designCode = "ACI",
                exportType = "DXF",
                filePath = "/storage/emulated/0/Download/Column_ACI.dxf",
                fileSizeBytes = 85000,
                createdAt = Date(1_700_000_001_000L)
            )
        )
        assertEquals(1L, pdfId)
        assertEquals(2L, dxfId)

        // جلب جميع تصديرات المشروع — الاستعلام يرتب تنازليًا حسب createdAt (الأحدث أولًا)
        val exports = dao.getExportsForProjectOnce(projectId)
        assertEquals(2, exports.size)
        assertEquals("DXF", exports[0].exportType)
        assertEquals("PDF", exports[1].exportType)
        assertEquals(projectId, exports[0].projectId)

        // حذف تصدير PDF والتحقق من بقاء DXF فقط
        dao.deleteExport(pdfId)
        val remaining = dao.getExportsForProjectOnce(projectId)
        assertEquals(1, remaining.size)
        assertEquals("DXF", remaining[0].exportType)
        assertEquals(dxfId, remaining[0].id)
    }

    @Test
    fun getAllExports_returnsAll() = runBlocking {
        val p1 = db.projectDao().insertProject(Project(name = "مشروع A", code = "ECP"))
        val p2 = db.projectDao().insertProject(Project(name = "مشروع B", code = "ACI"))

        dao.insertExport(
            ExportEntity(
                projectId = p1,
                elementType = "كمرة",
                designCode = "ECP",
                exportType = "PDF",
                filePath = "/a.pdf",
                createdAt = Date(1_700_000_000_000L)
            )
        )
        dao.insertExport(
            ExportEntity(
                projectId = p2,
                elementType = "عمود",
                designCode = "ACI",
                exportType = "DXF",
                filePath = "/b.dxf",
                createdAt = Date(1_700_000_001_000L)
            )
        )

        val all = dao.getAllExportsOnce()
        assertEquals(2, all.size)
        assertEquals(setOf(p1, p2), all.map { it.projectId }.toSet())
    }
}
