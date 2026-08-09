package com.puce.sigpel.services

import com.puce.sigpel.dto.EquipmentRequest
import com.puce.sigpel.dto.EquipmentStatusRequest
import com.puce.sigpel.entities.Equipment
import com.puce.sigpel.entities.EquipmentStatus
import com.puce.sigpel.exceptions.DuplicateResourceException
import com.puce.sigpel.exceptions.ResourceNotFoundException
import com.puce.sigpel.repositories.EquipmentRepository
import com.puce.sigpel.storage.S3Service
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

@Service
@Transactional
class EquipmentService(
    private val equipmentRepository: EquipmentRepository,
    private val equipmentCategoryService: EquipmentCategoryService,
    private val s3Service: S3Service
) {
    private val log = LoggerFactory.getLogger(EquipmentService::class.java)

    companion object {
        private val ALLOWED_IMAGE_TYPES = setOf("image/jpeg", "image/png")
        private const val MAX_IMAGE_SIZE_BYTES = 5L * 1024 * 1024
    }

    @Transactional(readOnly = true)
    fun list(categoryId: Long?, status: EquipmentStatus?): List<Equipment> {
        // Validates that the category exists before filtering; throws ResourceNotFoundException if not.
        categoryId?.let { equipmentCategoryService.get(it) }

        return when {
            categoryId == null && status == null -> equipmentRepository.findAllWithCategory()
            categoryId == null -> equipmentRepository.findByStatusWithCategory(status!!)
            else -> equipmentRepository.findByCategoryAndStatus(categoryId, status)
        }
    }

    @Transactional(readOnly = true)
    fun get(id: Long): Equipment =
        equipmentRepository.findById(id)
            .orElseThrow { ResourceNotFoundException("Equipment $id not found") }

    fun create(request: EquipmentRequest): Equipment {
        val category = equipmentCategoryService.get(request.categoryId)
        if (equipmentRepository.existsBySerialNumber(request.serialNumber)) {
            log.warn("event=equipment.rejected | msg=Duplicate serial number | serialNumber=\"${request.serialNumber}\"")
            throw DuplicateResourceException("Equipment with serial number '${request.serialNumber}' already exists")
        }
        val equipment = Equipment(
            category = category,
            name = request.name,
            serialNumber = request.serialNumber,
            description = request.description
        )
        val saved = equipmentRepository.save(equipment)
        log.info("event=equipment.created | msg=Equipment created | equipmentId=${saved.id} categoryId=${category.id} name=\"${saved.name}\" serialNumber=\"${saved.serialNumber}\"")
        return saved
    }

    fun updateStatus(id: Long, request: EquipmentStatusRequest): Equipment {
        val equipment = get(id)
        val previousStatus = equipment.status
        equipment.status = request.status
        val saved = equipmentRepository.save(equipment)
        log.info("event=equipment.status_changed | msg=Equipment status changed | equipmentId=${saved.id} from=$previousStatus to=${saved.status}")
        return saved
    }

    fun uploadImage(id: Long, file: MultipartFile): Equipment {
        val equipment = get(id)

        if (file.isEmpty) {
            throw IllegalArgumentException("The image file is required")
        }
        if (file.contentType !in ALLOWED_IMAGE_TYPES) {
            throw IllegalArgumentException("Only image/jpeg and image/png files are allowed")
        }
        if (file.size > MAX_IMAGE_SIZE_BYTES) {
            throw IllegalArgumentException("The image cannot exceed 5MB")
        }

        val url = s3Service.uploadImage(id, file)
        equipment.imageUrl = url
        val saved = equipmentRepository.save(equipment)
        log.info("event=equipment.image_updated | msg=Equipment image updated | equipmentId=${saved.id} imageUrl=\"${saved.imageUrl}\"")
        return saved
    }

    fun delete(id: Long) {
        val equipment = get(id)
        equipmentRepository.delete(equipment)
        log.info("event=equipment.deleted | msg=Equipment deleted | equipmentId=${equipment.id}")
    }
}
