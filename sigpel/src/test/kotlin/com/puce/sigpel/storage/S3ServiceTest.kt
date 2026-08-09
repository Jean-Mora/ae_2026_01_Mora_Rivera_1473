package com.puce.sigpel.storage

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.s3.model.S3Exception

class S3ServiceTest {

    private val s3Client = mockk<S3Client>()
    private val s3Service = S3Service(s3Client, "sigpel-equipos-imagenes-jpmora", "us-east-1")

    @Test
    fun `uploadImage stores the file under equipment id and returns its public url`() {
        val requestSlot = slot<PutObjectRequest>()
        every { s3Client.putObject(capture(requestSlot), any<RequestBody>()) } returns PutObjectResponse.builder().build()
        val file = MockMultipartFile("file", "photo.jpg", "image/jpeg", "fake-bytes".toByteArray())

        val url = s3Service.uploadImage(1L, file)

        assertEquals("sigpel-equipos-imagenes-jpmora", requestSlot.captured.bucket())
        assertTrue(requestSlot.captured.key().startsWith("equipment/1/"))
        assertTrue(requestSlot.captured.key().endsWith("-photo.jpg"))
        assertEquals("image/jpeg", requestSlot.captured.contentType())
        assertEquals(
            "https://sigpel-equipos-imagenes-jpmora.s3.us-east-1.amazonaws.com/${requestSlot.captured.key()}",
            url
        )
    }

    @Test
    fun `uploadImage sanitizes unsafe characters in the original filename`() {
        every { s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>()) } returns PutObjectResponse.builder().build()
        val file = MockMultipartFile("file", "my photo (1).jpg", "image/jpeg", "bytes".toByteArray())

        val url = s3Service.uploadImage(2L, file)

        assertTrue(url.contains("my_photo__1_.jpg"))
    }

    @Test
    fun `uploadImage wraps SDK failures without leaking internal details`() {
        every { s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>()) } throws S3Exception.builder().message("Access Denied").build()
        val file = MockMultipartFile("file", "photo.jpg", "image/jpeg", "bytes".toByteArray())

        val ex = assertThrows(RuntimeException::class.java) {
            s3Service.uploadImage(1L, file)
        }
        assertEquals("Failed to upload image to S3", ex.message)
        verify(exactly = 1) { s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>()) }
    }
}
