package com.subocol.taller.agenda.infrastructure.persistence.entity;

import com.subocol.taller.agenda.application.dto.*;
import com.subocol.taller.agenda.domain.model.*;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "notificacion_cita")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificacionJpaEntity {
  @Id
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 36)
  private UUID id;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "cita_id", length = 36, nullable = false, updatable = false)
  private UUID citaId;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "orden_id", length = 36, nullable = false, updatable = false)
  private UUID ordenId;

  @Enumerated(EnumType.STRING)
  @Column(length = 30, nullable = false, updatable = false)
  private EventoNotificacion evento;

  @Column(name = "orden_evento", nullable = false, updatable = false)
  private int ordenEvento;

  @Enumerated(EnumType.STRING)
  @Column(name = "tipo_cita", length = 20, nullable = false, updatable = false)
  private TipoCita tipoCita;

  @Column(length = 100, nullable = false, updatable = false)
  private String asesor;

  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  @Column(nullable = false, updatable = false)
  private Instant inicio;

  @Enumerated(EnumType.STRING)
  @Column(length = 20, nullable = false)
  private EstadoNotificacion estado;

  @Column(nullable = false)
  private int intentos;

  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  @Column(nullable = false, updatable = false)
  private Instant creada;

  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  @Column(name = "siguiente_intento", nullable = false)
  private Instant siguienteIntento;

  @JdbcTypeCode(SqlTypes.TIMESTAMP)
  @Column(name = "reserva_hasta")
  private Instant reservaHasta;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "reserva_token", length = 36)
  private UUID reservaToken;

  @Column(name = "codigo_error", length = 60)
  private String codigoError;

  @Column(name = "mensaje_proveedor_id", length = 300)
  private String mensajeProveedorId;

  @Version private Long version;

  public static NotificacionJpaEntity pendiente(
      CitaDetalle c, EventoNotificacion evento, Instant ahora) {
    var e = new NotificacionJpaEntity();
    e.id = UUID.randomUUID();
    e.citaId = c.id();
    e.ordenId = c.ordenId();
    e.evento = evento;
    e.ordenEvento = evento.ordinal();
    e.tipoCita = c.tipo();
    e.asesor = c.asesor();
    e.inicio = c.inicio();
    e.estado = EstadoNotificacion.PENDIENTE;
    e.creada = ahora;
    e.siguienteIntento = ahora;
    return e;
  }

  public EnvioReclamado reclamar(Instant ahora, int segundos) {
    estado = EstadoNotificacion.EN_PROCESO;
    reservaToken = UUID.randomUUID();
    reservaHasta = ahora.plusSeconds(segundos);
    intentos++;
    return new EnvioReclamado(
        new NotificacionCita(id, citaId, ordenId, evento, tipoCita, asesor, inicio),
        reservaToken,
        intentos);
  }

  public boolean corresponde(UUID token) {
    return estado == EstadoNotificacion.EN_PROCESO && token.equals(reservaToken);
  }

  public void completar(ResultadoEnvio resultado) {
    estado = resultado.estado();
    mensajeProveedorId = resultado.mensajeProveedorId();
    codigoError = null;
    liberar();
  }

  public void fallar(String codigo, Instant siguiente, boolean definitivo) {
    estado = definitivo ? EstadoNotificacion.FALLIDA : EstadoNotificacion.PENDIENTE;
    codigoError = codigo;
    siguienteIntento = siguiente;
    liberar();
  }

  public void reintentar(Instant ahora) {
    if (estado != EstadoNotificacion.FALLIDA)
      throw new com.subocol.taller.agenda.domain.exception.ReglaNegocioException(
          "Solo se reintentan manualmente notificaciones fallidas");
    estado = EstadoNotificacion.PENDIENTE;
    intentos = 0;
    codigoError = null;
    siguienteIntento = ahora;
    liberar();
  }

  private void liberar() {
    reservaToken = null;
    reservaHasta = null;
  }

  public NotificacionDetalle detalle() {
    return new NotificacionDetalle(
        id,
        citaId,
        evento,
        estado,
        intentos,
        creada,
        siguienteIntento,
        codigoError,
        mensajeProveedorId);
  }
}
