package com.subocol.taller.agenda.infrastructure.persistence.entity;

import com.subocol.taller.agenda.domain.model.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "cita")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CitaJpaEntity {
  @Id
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 36)
  private UUID id;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "orden_id", length = 36, nullable = false)
  private UUID ordenId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "franja_id", nullable = false, updatable = false)
  private FranjaJpaEntity franja;

  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false)
  private TipoCita tipo;

  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false)
  private EstadoCita estado;

  @Column(name = "creada_por", length = 100, nullable = false)
  private String creadaPor;

  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  @Column(name = "fecha_creacion", nullable = false)
  private Instant fechaCreacion;

  @Column(name = "motivo_cancelacion", length = 500)
  private String motivoCancelacion;

  @Column(name = "cancelada_por", length = 100)
  private String canceladaPor;

  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  @Column(name = "fecha_cancelacion")
  private Instant fechaCancelacion;

  @Version private Long version;

  public static CitaJpaEntity desde(Cita c, FranjaJpaEntity franja) {
    var e = new CitaJpaEntity();
    e.id = c.getId();
    e.ordenId = c.getOrdenId();
    e.franja = franja;
    e.tipo = c.getTipo();
    e.estado = c.getEstado();
    e.creadaPor = c.getCreadaPor();
    e.fechaCreacion = c.getFechaCreacion();
    return e;
  }

  public Cita aDominio() {
    return Cita.reconstituir(
        id,
        ordenId,
        franja.getId(),
        tipo,
        estado,
        creadaPor,
        fechaCreacion,
        motivoCancelacion,
        canceladaPor,
        fechaCancelacion);
  }

  public void aplicarCancelacion(Cita c) {
    estado = c.getEstado();
    motivoCancelacion = c.getMotivoCancelacion();
    canceladaPor = c.getCanceladaPor();
    fechaCancelacion = c.getFechaCancelacion();
  }
}
