package com.puce.sigpel.controllers

import com.puce.sigpel.dto.EquipmentRequest
import com.puce.sigpel.dto.EquipmentResponse
import com.puce.sigpel.dto.EquipmentStatusRequest
import com.puce.sigpel.entities.EquipmentStatus
import com.puce.sigpel.mappers.toResponse
import com.puce.sigpel.services.EquipmentService
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/equipment")
class EquipmentController(
    private val equipmentService: EquipmentService
) {
    // HU-23: GET /equipment?categoryId=3&status=AVAILABLE (both optional, combinable)
    @GetMapping
    fun list(
        @RequestParam(required = false) categoryId: Long?,
        @RequestParam(required = false) status: EquipmentStatus?
    ): List<EquipmentResponse> =
        equipmentService.list(categoryId, status).map { it.toResponse() }

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): EquipmentResponse =
        equipmentService.get(id).toResponse()

    @PostMapping
    @PreAuthorize("hasRole('ENCARGADO')")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: EquipmentRequest): EquipmentResponse =
        equipmentService.create(request).toResponse()

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ENCARGADO')")
    fun updateStatus(@PathVariable id: Long, @Valid @RequestBody request: EquipmentStatusRequest): EquipmentResponse =
        equipmentService.updateStatus(id, request).toResponse()

    @PostMapping("/{id}/image")
    @PreAuthorize("hasRole('ENCARGADO')")
    fun uploadImage(@PathVariable id: Long, @RequestParam("file") file: MultipartFile): EquipmentResponse =
        equipmentService.uploadImage(id, file).toResponse()

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ENCARGADO')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) = equipmentService.delete(id)
}
