package com.puce.sigpel.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client

@Configuration
open class S3Config {

    @Value("\${aws.s3.region}")
    private lateinit var region: String

    // DefaultCredentialsProvider: en EC2 toma las credenciales del IAM Role
    // adjunto a la instancia (sigpel-ec2-s3-role). Nunca hardcodear
    // access key/secret key aqui.
    @Bean
    open fun s3Client(): S3Client =
        S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build()
}
