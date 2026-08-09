package com.puce.sigpel.services

import com.puce.sigpel.dto.IncidentRequest
import com.puce.sigpel.entities.Equipment
import com.puce.sigpel.entities.EquipmentCategory
import com.puce.sigpel.entities.Incident
import com.puce.sigpel.entities.IncidentType
import com.puce.sigpel.entities.Loan
import com.puce.sigpel.repositories.IncidentRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class IncidentServiceTest {

    private val incidentRepository = mockk<IncidentRepository>()
    private val loanService = mockk<LoanService>()
    private lateinit var incidentService: IncidentService
    private lateinit var loan: Loan

    @BeforeEach
    fun setUp() {
        incidentService = IncidentService(incidentRepository, loanService)
        loan = Loan(
            id = 1L,
            equipment = Equipment(id = 1L, category = EquipmentCategory(id = 1L, name = "Electronics"), name = "Multimeter"),
            studentUser = "student01"
        )
    }

    @Test
    fun `register allows a second incident for the same loan`() {
        every { loanService.get(1L) } returns loan
        every { incidentRepository.save(any()) } answers { firstArg() }

        incidentService.register(IncidentRequest(loanId = 1L, type = IncidentType.DAMAGE))
        incidentService.register(IncidentRequest(loanId = 1L, type = IncidentType.DELAY))

        verify(exactly = 2) { incidentRepository.save(any()) }
    }

    @Test
    fun `register saves an incident linked to the requested loan`() {
        every { loanService.get(1L) } returns loan
        val savedSlot = slot<Incident>()
        every { incidentRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

        incidentService.register(IncidentRequest(loanId = 1L, type = IncidentType.LOSS, description = "Lost in transit"))

        assert(savedSlot.captured.loan === loan)
        assert(savedSlot.captured.type == IncidentType.LOSS)
    }

    @Test
    fun `list returns all incidents ordered by report date descending`() {
        val incidents = listOf(
            Incident(id = 2L, loan = loan, type = IncidentType.DELAY),
            Incident(id = 1L, loan = loan, type = IncidentType.DAMAGE)
        )
        every { incidentRepository.findAllByOrderByReportDateDesc() } returns incidents

        val result = incidentService.list()

        assert(result == incidents)
    }
}
