package com.puce.sigpel.services

import com.puce.sigpel.dto.EquipmentRequest
import com.puce.sigpel.entities.Equipment
import com.puce.sigpel.entities.EquipmentCategory
import com.puce.sigpel.entities.EquipmentStatus
import com.puce.sigpel.exceptions.DuplicateResourceException
import com.puce.sigpel.exceptions.ResourceNotFoundException
import com.puce.sigpel.repositories.EquipmentRepository
import com.puce.sigpel.storage.S3Service
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import java.util.Optional

class EquipmentServiceTest {

    private val equipmentRepository = mockk<EquipmentRepository>()
    private val equipmentCategoryService = mockk<EquipmentCategoryService>()
    private val s3Service = mockk<S3Service>()
    private lateinit var equipmentService: EquipmentService

    @BeforeEach
    fun setUp() {
        equipmentService = EquipmentService(equipmentRepository, equipmentCategoryService, s3Service)
    }

    @Test
    fun `create links the equipment to the existing category and defaults to AVAILABLE`() {
        val category = EquipmentCategory(id = 1L, name = "Electronics")
        every { equipmentCategoryService.get(1L) } returns category
        every { equipmentRepository.existsBySerialNumber(any()) } returns false
        val savedSlot = slot<Equipment>()
        every { equipmentRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

        equipmentService.create(EquipmentRequest(categoryId = 1L, name = "Oscilloscope", serialNumber = "SN-001"))

        assertEquals("Oscilloscope", savedSlot.captured.name)
        assertEquals("SN-001", savedSlot.captured.serialNumber)
        assertEquals(EquipmentStatus.AVAILABLE, savedSlot.captured.status)
        assertEquals(category, savedSlot.captured.category)
    }

    @Test
    fun `create allows multiple equipment items with the same name in the same category`() {
        val category = EquipmentCategory(id = 1L, name = "Electronics")
        every { equipmentCategoryService.get(1L) } returns category
        every { equipmentRepository.existsBySerialNumber(any()) } returns false
        every { equipmentRepository.save(any()) } answers { firstArg() }

        equipmentService.create(EquipmentRequest(categoryId = 1L, name = "Multimeter", serialNumber = "SN-002"))
        equipmentService.create(EquipmentRequest(categoryId = 1L, name = "Multimeter", serialNumber = "SN-003"))

        verify(exactly = 2) { equipmentRepository.save(any()) }
    }

    @Test
    fun `create throws DuplicateResourceException when the serial number already exists`() {
        val category = EquipmentCategory(id = 1L, name = "Electronics")
        every { equipmentCategoryService.get(1L) } returns category
        every { equipmentRepository.existsBySerialNumber("SN-004") } returns true

        assertThrows(DuplicateResourceException::class.java) {
            equipmentService.create(EquipmentRequest(categoryId = 1L, name = "Multimeter", serialNumber = "SN-004"))
        }

        verify(exactly = 0) { equipmentRepository.save(any()) }
    }

    @Test
    fun `get throws ResourceNotFoundException when the equipment does not exist`() {
        every { equipmentRepository.findById(99L) } returns Optional.empty()

        assertThrows(ResourceNotFoundException::class.java) {
            equipmentService.get(99L)
        }
    }

    @Test
    fun `delete removes the equipment when it exists`() {
        val equipment = Equipment(id = 2L, category = EquipmentCategory(id = 1L, name = "Electronics"), name = "Multimeter")
        every { equipmentRepository.findById(2L) } returns Optional.of(equipment)
        every { equipmentRepository.delete(equipment) } returns Unit

        equipmentService.delete(2L)

        verify(exactly = 1) { equipmentRepository.delete(equipment) }
    }

    // --- HU-23: list() with combined filters ---

    @Test
    fun `list without filters returns every equipment item with its category`() {
        val category = EquipmentCategory(id = 1L, name = "Electronics")
        val equipment = Equipment(id = 1L, category = category, name = "Multimeter")
        every { equipmentRepository.findAllWithCategory() } returns listOf(equipment)

        val result = equipmentService.list(categoryId = null, status = null)

        assertEquals(1, result.size)
        verify(exactly = 1) { equipmentRepository.findAllWithCategory() }
        verify(exactly = 0) { equipmentCategoryService.get(any()) }
    }

    @Test
    fun `list with status only uses findByStatusWithCategory and does not validate category`() {
        val category = EquipmentCategory(id = 1L, name = "Electronics")
        val equipment = Equipment(id = 1L, category = category, name = "Multimeter", status = EquipmentStatus.MAINTENANCE)
        every { equipmentRepository.findByStatusWithCategory(EquipmentStatus.MAINTENANCE) } returns listOf(equipment)

        val result = equipmentService.list(categoryId = null, status = EquipmentStatus.MAINTENANCE)

        assertEquals(1, result.size)
        verify(exactly = 1) { equipmentRepository.findByStatusWithCategory(EquipmentStatus.MAINTENANCE) }
        verify(exactly = 0) { equipmentCategoryService.get(any()) }
    }

    @Test
    fun `list with category only validates its existence and uses findByCategoryAndStatus`() {
        val category = EquipmentCategory(id = 1L, name = "Electronics")
        val equipment = Equipment(id = 1L, category = category, name = "Multimeter")
        every { equipmentCategoryService.get(1L) } returns category
        every { equipmentRepository.findByCategoryAndStatus(1L, null) } returns listOf(equipment)

        val result = equipmentService.list(categoryId = 1L, status = null)

        assertEquals(1, result.size)
        verify(exactly = 1) { equipmentCategoryService.get(1L) }
        verify(exactly = 1) { equipmentRepository.findByCategoryAndStatus(1L, null) }
    }

    @Test
    fun `list with category and status combined`() {
        val category = EquipmentCategory(id = 1L, name = "Electronics")
        val equipment = Equipment(id = 1L, category = category, name = "Multimeter", status = EquipmentStatus.AVAILABLE)
        every { equipmentCategoryService.get(1L) } returns category
        every { equipmentRepository.findByCategoryAndStatus(1L, EquipmentStatus.AVAILABLE) } returns listOf(equipment)

        val result = equipmentService.list(categoryId = 1L, status = EquipmentStatus.AVAILABLE)

        assertEquals(1, result.size)
        verify(exactly = 1) { equipmentRepository.findByCategoryAndStatus(1L, EquipmentStatus.AVAILABLE) }
    }

    @Test
    fun `list with a nonexistent category throws ResourceNotFoundException and does not query equipment`() {
        every { equipmentCategoryService.get(99L) } throws ResourceNotFoundException("Category 99 not found")

        assertThrows(ResourceNotFoundException::class.java) {
            equipmentService.list(categoryId = 99L, status = null)
        }

        verify(exactly = 0) { equipmentRepository.findByCategoryAndStatus(any(), any()) }
        verify(exactly = 0) { equipmentRepository.findAllWithCategory() }
    }

    // --- uploadImage() ---

    @Test
    fun `uploadImage uploads to S3 and saves the returned url`() {
        val equipment = Equipment(id = 1L, category = EquipmentCategory(id = 1L, name = "Electronics"), name = "Multimeter")
        val file = MockMultipartFile("file", "photo.jpg", "image/jpeg", "fake-image-bytes".toByteArray())
        every { equipmentRepository.findById(1L) } returns Optional.of(equipment)
        every { s3Service.uploadImage(1L, file) } returns "https://bucket.s3.us-east-1.amazonaws.com/equipment/1/photo.jpg"
        every { equipmentRepository.save(any()) } answers { firstArg() }

        val result = equipmentService.uploadImage(1L, file)

        assertEquals("https://bucket.s3.us-east-1.amazonaws.com/equipment/1/photo.jpg", result.imageUrl)
        verify(exactly = 1) { equipmentRepository.save(equipment) }
    }

    @Test
    fun `uploadImage throws ResourceNotFoundException when the equipment does not exist`() {
        val file = MockMultipartFile("file", "photo.jpg", "image/jpeg", "bytes".toByteArray())
        every { equipmentRepository.findById(99L) } returns Optional.empty()

        assertThrows(ResourceNotFoundException::class.java) {
            equipmentService.uploadImage(99L, file)
        }
        verify(exactly = 0) { s3Service.uploadImage(any(), any()) }
    }

    @Test
    fun `uploadImage rejects a file type other than jpeg or png`() {
        val equipment = Equipment(id = 1L, category = EquipmentCategory(id = 1L, name = "Electronics"), name = "Multimeter")
        val file = MockMultipartFile("file", "doc.pdf", "application/pdf", "bytes".toByteArray())
        every { equipmentRepository.findById(1L) } returns Optional.of(equipment)

        assertThrows(IllegalArgumentException::class.java) {
            equipmentService.uploadImage(1L, file)
        }
        verify(exactly = 0) { s3Service.uploadImage(any(), any()) }
    }

    @Test
    fun `uploadImage rejects a file larger than 5MB`() {
        val equipment = Equipment(id = 1L, category = EquipmentCategory(id = 1L, name = "Electronics"), name = "Multimeter")
        val oversized = ByteArray(5 * 1024 * 1024 + 1)
        val file = MockMultipartFile("file", "photo.jpg", "image/jpeg", oversized)
        every { equipmentRepository.findById(1L) } returns Optional.of(equipment)

        assertThrows(IllegalArgumentException::class.java) {
            equipmentService.uploadImage(1L, file)
        }
        verify(exactly = 0) { s3Service.uploadImage(any(), any()) }
    }

    @Test
    fun `uploadImage rejects an empty file`() {
        val equipment = Equipment(id = 1L, category = EquipmentCategory(id = 1L, name = "Electronics"), name = "Multimeter")
        val file = MockMultipartFile("file", "photo.jpg", "image/jpeg", ByteArray(0))
        every { equipmentRepository.findById(1L) } returns Optional.of(equipment)

        assertThrows(IllegalArgumentException::class.java) {
            equipmentService.uploadImage(1L, file)
        }
        verify(exactly = 0) { s3Service.uploadImage(any(), any()) }
    }
}
