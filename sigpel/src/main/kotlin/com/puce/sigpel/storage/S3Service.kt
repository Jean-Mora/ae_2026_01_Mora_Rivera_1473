package com.puce.sigpel.storage

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.util.UUID

@Service
class S3Service(
    private val s3Client: S3Client,
    @param:Value("\${aws.s3.bucket}") private val bucket: String,
    @param:Value("\${aws.s3.region}") private val region: String
) {
    private val log = LoggerFactory.getLogger(S3Service::class.java)

    // El bucket tiene una bucket policy de lectura publica (s3:GetObject para
    // "*"), asi que la URL del objeto es accesible directamente sin
    // presigned URLs.
    fun uploadImage(equipmentId: Long, file: MultipartFile): String {
        val key = buildKey(equipmentId, file.originalFilename)
        try {
            s3Client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.contentType)
                    .build(),
                RequestBody.fromInputStream(file.inputStream, file.size)
            )
        } catch (ex: Exception) {
            // No se propagan detalles internos del SDK al cliente: se loguean
            // aqui y se relanza un mensaje generico (ver GlobalExceptionHandler).
            log.error("event=equipment.image_upload_failed | msg=Failed to upload image to S3 | equipmentId=$equipmentId key=\"$key\"", ex)
            throw RuntimeException("Failed to upload image to S3")
        }

        val url = "https://$bucket.s3.$region.amazonaws.com/$key"
        log.info("event=equipment.image_uploaded | msg=Image uploaded to S3 | equipmentId=$equipmentId key=\"$key\"")
        return url
    }

    private fun buildKey(equipmentId: Long, originalFilename: String?): String {
        val sanitized = (originalFilename ?: "image").replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return "equipment/$equipmentId/${UUID.randomUUID()}-$sanitized"
    }
}
