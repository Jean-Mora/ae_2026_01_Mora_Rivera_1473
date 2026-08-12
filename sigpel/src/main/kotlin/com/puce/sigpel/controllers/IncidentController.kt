package com.puce.sigpel.controllers

import com.puce.sigpel.dto.IncidentRequest
import com.puce.sigpel.dto.IncidentResponse
import com.puce.sigpel.mappers.toResponse
import com.puce.sigpel.services.IncidentService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/incidents")
@PreAuthorize("hasRole('ENCARGADO')")
class IncidentController(
    private val incidentService: IncidentService
) {
    @GetMapping
    fun list(): List<IncidentResponse> =
        incidentService.list().map { it.toResponse() }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody request: IncidentRequest): IncidentResponse =
        incidentService.register(request).toResponse()

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): IncidentResponse =
        incidentService.get(id).toResponse()

    @PatchMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody request: IncidentRequest): IncidentResponse =
        incidentService.update(id, request).toResponse()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = incidentService.delete(id)
}
