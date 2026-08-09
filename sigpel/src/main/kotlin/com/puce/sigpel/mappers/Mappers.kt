package com.puce.sigpel.mappers

import com.puce.sigpel.dto.EquipmentCategoryResponse
import com.puce.sigpel.dto.EquipmentResponse
import com.puce.sigpel.dto.IncidentResponse
import com.puce.sigpel.dto.LoanResponse
import com.puce.sigpel.entities.Equipment
import com.puce.sigpel.entities.EquipmentCategory
import com.puce.sigpel.entities.Incident
import com.puce.sigpel.entities.Loan

fun EquipmentCategory.toResponse() = EquipmentCategoryResponse(
    id = requireNotNull(id),
    name = name
)

fun Equipment.toResponse() = EquipmentResponse(
    id = requireNotNull(id),
    categoryId = requireNotNull(category.id),
    categoryName = category.name,
    name = name,
    serialNumber = serialNumber,
    status = status,
    description = description,
    imageUrl = imageUrl
)

fun Loan.toResponse() = LoanResponse(
    id = requireNotNull(id),
    equipmentId = requireNotNull(equipment.id),
    equipmentName = equipment.name,
    studentUser = studentUser,
    requestDate = requestDate,
    estimatedReturnDate = estimatedReturnDate,
    actualReturnDate = actualReturnDate,
    status = status,
    comment = comment
)

fun Incident.toResponse() = IncidentResponse(
    id = requireNotNull(id),
    loanId = requireNotNull(loan.id),
    type = type,
    description = description,
    reportDate = reportDate
)
