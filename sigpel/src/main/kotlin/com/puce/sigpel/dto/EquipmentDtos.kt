package com.puce.sigpel.dto

import com.puce.sigpel.entities.EquipmentStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class EquipmentRequest(
    @field:NotNull(message = "Category is required")
    val categoryId: Long,

    @field:NotBlank(message = "Name is required")
    @field:Size(max = 80, message = "Name cannot exceed 80 characters")
    val name: String,

    // Identifica la unidad fisica especifica (a diferencia de "name", que
    // puede repetirse entre varios items identicos de inventario).
    @field:NotBlank(message = "Serial number is required")
    @field:Size(max = 60, message = "Serial number cannot exceed 60 characters")
    val serialNumber: String,

    @field:Size(max = 255, message = "Description cannot exceed 255 characters")
    val description: String? = null
)

data class EquipmentStatusRequest(
    @field:NotNull(message = "Status is required")
    val status: EquipmentStatus
)

data class EquipmentResponse(
    val id: Long,
    val categoryId: Long,
    val categoryName: String,
    val name: String,
    val serialNumber: String?,
    val status: EquipmentStatus,
    val description: String?,
    val imageUrl: String?
)
