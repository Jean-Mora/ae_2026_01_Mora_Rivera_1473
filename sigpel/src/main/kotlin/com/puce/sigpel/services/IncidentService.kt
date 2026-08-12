package com.puce.sigpel.services

import com.puce.sigpel.dto.IncidentRequest
import com.puce.sigpel.entities.Incident
import com.puce.sigpel.exceptions.ResourceNotFoundException
import com.puce.sigpel.repositories.IncidentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class IncidentService(
    private val incidentRepository: IncidentRepository,
    private val loanService: LoanService
) {
    private val log = LoggerFactory.getLogger(IncidentService::class.java)

    @Transactional(readOnly = true)
    fun get(id: Long): Incident =
        incidentRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Incident $id not found") }

    @Transactional(readOnly = true)
    fun list(): List<Incident> = incidentRepository.findAllByOrderByReportDateDesc()

    /** The loan-incident relationship is 1:N: a loan can have several incidents. */
    fun register(request: IncidentRequest): Incident {
        val loan = loanService.get(request.loanId)
        val incident = Incident(
            loan = loan,
            type = request.type,
            description = request.description
        )
        val saved = incidentRepository.save(incident)
        log.info("event=incident.registered | msg=Incident registered | incidentId=${saved.id} loanId=${loan.id} type=${saved.type}")
        return saved
    }

    fun update(id: Long, request: IncidentRequest): Incident {
        val incident = get(id)
        incident.type = request.type
        incident.description = request.description
        val saved = incidentRepository.save(incident)
        log.info("event=incident.updated | msg=Incident updated | incidentId=${saved.id}")
        return saved
    }

    fun delete(id: Long) {
        val incident = get(id)
        incidentRepository.delete(incident)
        log.info("event=incident.deleted | msg=Incident deleted | incidentId=${incident.id}")
    }
}
