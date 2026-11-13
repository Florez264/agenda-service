package com.subocol.taller.agenda.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "control_orden")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ControlOrdenJpaEntity {
  @Id
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "orden_id", length = 36)
  private UUID ordenId;

  @Column(name = "ultimo_cierre", nullable = false)
  private long ultimoCierre;

  public static ControlOrdenJpaEntity crear(UUID id) {
    var c = new ControlOrdenJpaEntity();
    c.ordenId = id;
    c.ultimoCierre = -1;
    return c;
  }

  public void registrar(long version) {
    ultimoCierre = Math.max(ultimoCierre, version);
  }
}
