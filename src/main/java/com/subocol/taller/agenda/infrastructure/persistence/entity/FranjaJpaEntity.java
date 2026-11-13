package com.subocol.taller.agenda.infrastructure.persistence.entity;

import com.subocol.taller.agenda.domain.model.Franja;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(
    name = "franja",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_asesor_inicio",
            columnNames = {"asesor", "inicio"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FranjaJpaEntity {
  @Id
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 36)
  private UUID id;

  @Column(length = 100, nullable = false, updatable = false)
  private String asesor;

  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  @Column(nullable = false, updatable = false)
  private Instant inicio;

  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  @Column(nullable = false, updatable = false)
  private Instant fin;

  @Column(nullable = false, updatable = false)
  private int cupo;

  public static FranjaJpaEntity desde(Franja f) {
    var e = new FranjaJpaEntity();
    e.id = f.id();
    e.asesor = f.asesor();
    e.inicio = f.inicio();
    e.fin = f.fin();
    e.cupo = f.cupo();
    return e;
  }

  public Franja aDominio() {
    return new Franja(id, asesor, inicio, fin, cupo);
  }
}
