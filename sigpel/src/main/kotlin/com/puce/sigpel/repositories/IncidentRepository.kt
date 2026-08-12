package com.puce.sigpel.repositories

import com.puce.sigpel.entities.Incident
import org.springframework.data.jpa.repository.JpaRepository

interface IncidentRepository : JpaRepository<Incident, Long> {
    fun findAllByOrderByReportDateDesc(): List<Incident>
}
