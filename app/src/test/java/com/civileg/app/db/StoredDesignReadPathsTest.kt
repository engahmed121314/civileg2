package com.civileg.app.db

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.civileg.app.domain.entities.MaterialPrices
import com.civileg.app.domain.usecases.CalculateElementBoq
import com.civileg.app.viewmodel.BOQViewModel
import com.civileg.app.viewmodel.ProjectViewModel
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.InvocationTargetException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StoredDesignReadPathsTest {
    private fun legacy() = Design(
        id = 17L, projectId = 2L, type = DesignType.SLAB, name = "Old ribbed slab",
        inputData = "{}", results = """{"type":"HOLLOW_BLOCK","code":"ECP","isSafe":true}""",
        isSafe = true, codeUsed = "Egyptian Code", concreteVolume = 3.0, steelWeight = 60.0
    )

    private fun repository(dao: DesignDao) = DesignRepository(
        dao, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
        mockk(relaxed = true), mockk(relaxed = true)
    )

    @Test fun `open by ID and search normalize legacy SAFE without writing saved data`() = runBlocking {
        val old = legacy()
        val dao = mockk<DesignDao>()
        coEvery { dao.getDesignById(17L) } returns old
        every { dao.searchDesigns(any()) } returns flowOf(listOf(old))
        val repo = repository(dao)
        assertFalse(repo.getDesignById(17L)!!.isSafe)
        assertFalse(repo.searchDesigns("ribbed").first().single().isSafe)
        assertTrue(old.isSafe)
        coVerify(exactly = 0) { dao.insertDesign(any()) }
        coVerify(exactly = 0) { dao.deleteDesign(any()) }
    }

    @Test fun `project and archive reads show hold and summary counts it without dropping records`() = runBlocking {
        val old = legacy()
        val other = old.copy(id = 18L, codeUsed = "American Code",
            results = """{"type":"HOLLOW_BLOCK","code":"ACI","isSafe":true}""")
        val dao = mockk<DesignDao>()
        every { dao.getAllDesigns() } returns MutableLiveData(listOf(old, other))
        every { dao.getDesignsForProject(2L) } returns MutableLiveData(listOf(old, other))
        every { dao.getDesignsForProjectFlow(2L) } returns flowOf(listOf(old, other))
        val projects = mockk<ProjectDao>()
        every { projects.getAllProjects() } returns MutableLiveData(emptyList())
        every { projects.getActiveProjectCount() } returns MutableLiveData(0)
        val vm = ProjectViewModel(projects, dao, mockk(relaxed = true))
        var rows: List<Design> = emptyList()
        val observer = Observer<List<Design>> { rows = it }
        vm.allDesigns.observeForever(observer)
        assertEquals(2, rows.size)
        assertFalse(rows[0].isSafe)
        assertTrue(rows[1].isSafe)
        vm.allDesigns.removeObserver(observer)
        val summary = vm.getProjectSummary(2L).first()
        assertEquals(2, summary.designCount)
        assertEquals(1, summary.methodBlockedDesignCount)
        assertEquals(6.0, summary.totalConcrete, 0.0)
        coVerify(exactly = 0) { dao.insertDesign(any()) }
    }

    @Test fun `archived pre-flag design cannot enter detailed BOQ calculator`() {
        val calculator = mockk<CalculateElementBoq>(relaxed = true)
        val vm = BOQViewModel(mockk(relaxed = true), calculator)
        val method = BOQViewModel::class.java.getDeclaredMethod(
            "generateBoqFromDesign", Design::class.java, MaterialPrices::class.java
        ).apply { isAccessible = true }
        val error = runCatching { method.invoke(vm, legacy(), MaterialPrices()) }.exceptionOrNull()
        assertTrue(error is InvocationTargetException)
        assertTrue(error!!.cause is IllegalArgumentException)
        verify { calculator wasNot Called }
    }
}
