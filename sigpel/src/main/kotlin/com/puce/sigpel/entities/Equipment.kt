package com.puce.sigpel.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version

@Entity
@Table(name = "equipment")
class Equipment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    var category: EquipmentCategory,

    @Column(nullable = false, length = 80)
    var name: String,

    // Identificador fisico de ESTA unidad especifica (a diferencia de "name",
    // que puede repetirse entre varios items identicos de inventario).
    // Nullable a nivel de columna solo para que ddl-auto=update pueda agregar
    // la columna sin romper filas existentes; a nivel de API es obligatorio
    // (ver EquipmentRequest). Postgres permite varios NULL en una columna unique.
    @Column(name = "serial_number", unique = true, length = 60)
    var serialNumber: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: EquipmentStatus = EquipmentStatus.AVAILABLE,

    @Column(length = 255)
    var description: String? = null,

    // URL publica de la imagen en S3 (bucket con lectura publica), o null si
    // el equipo todavia no tiene una imagen cargada.
    @Column(name = "image_url", length = 500)
    var imageUrl: String? = null,

    // Optimistic locking: protects against two students requesting the same
    // equipment at the same time. If two transactions read the same version and
    // both try to save, the second one fails with ObjectOptimisticLockingFailureException
    // (translated to 409 Conflict in GlobalExceptionHandler).
    @Version
    var version: Long? = null
)
