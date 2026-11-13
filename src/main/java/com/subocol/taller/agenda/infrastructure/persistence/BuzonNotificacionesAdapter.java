package com.subocol.taller.agenda.infrastructure.persistence;

import com.subocol.taller.agenda.application.dto.*;
import com.subocol.taller.agenda.application.port.BuzonNotificacionesPort;
import com.subocol.taller.agenda.domain.exception.RecursoNoEncontradoException;
import com.subocol.taller.agenda.domain.model.EstadoNotificacion;
import com.subocol.taller.agenda.infrastructure.persistence.entity.NotificacionJpaEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
public class BuzonNotificacionesAdapter implements BuzonNotificacionesPort {
  private final EntityManager em;

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Optional<EnvioReclamado> reclamar(Instant ahora, int segundos) {
    String jpql =
        "select n from NotificacionJpaEntity n where ((n.estado=:pendiente and"
            + " n.siguienteIntento<=:ahora) or (n.estado=:procesando and n.reservaHasta<=:ahora))"
            + " and not exists (select p.id from NotificacionJpaEntity p where p.citaId=n.citaId"
            + " and p.ordenEvento<n.ordenEvento and p.estado in (:pendiente,:procesando)) order by"
            + " n.creada asc,n.ordenEvento asc,n.id asc";
    var filas =
        em.createQuery(jpql, NotificacionJpaEntity.class)
            .setParameter("pendiente", EstadoNotificacion.PENDIENTE)
            .setParameter("procesando", EstadoNotificacion.EN_PROCESO)
            .setParameter("ahora", ahora)
            .setMaxResults(1)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .getResultList();
    if (filas.isEmpty()) return Optional.empty();
    var envio = filas.getFirst().reclamar(ahora, segundos);
    em.flush();
    return Optional.of(envio);
  }

  private NotificacionJpaEntity bloquear(UUID id) {
    var e = em.find(NotificacionJpaEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
    if (e == null) throw new RecursoNoEncontradoException("Notificación no encontrada");
    return e;
  }

  @Transactional
  public void completar(EnvioReclamado envio, ResultadoEnvio resultado) {
    var e = bloquear(envio.notificacion().id());
    if (e.corresponde(envio.token())) e.completar(resultado);
  }

  @Transactional
  public void fallar(EnvioReclamado envio, String codigo, Instant siguiente, boolean definitivo) {
    var e = bloquear(envio.notificacion().id());
    if (e.corresponde(envio.token())) e.fallar(codigo, siguiente, definitivo);
  }

  public Pagina<NotificacionDetalle> listar(
      UUID citaId, EstadoNotificacion estado, Paginacion pagina) {
    String filtro =
        " where (:cita is null or n.citaId=:cita) and (:estado is null or n.estado=:estado)";
    var filas =
        em.createQuery(
                "select n from NotificacionJpaEntity n"
                    + filtro
                    + " order by n.creada desc,n.ordenEvento desc,n.id desc",
                NotificacionJpaEntity.class)
            .setParameter("cita", citaId)
            .setParameter("estado", estado)
            .setFirstResult(pagina.page() * pagina.size())
            .setMaxResults(pagina.size())
            .getResultList();
    long total =
        em.createQuery("select count(n) from NotificacionJpaEntity n" + filtro, Long.class)
            .setParameter("cita", citaId)
            .setParameter("estado", estado)
            .getSingleResult();
    int pages = (int) ((total + pagina.size() - 1) / pagina.size());
    return new Pagina<>(
        filas.stream().map(NotificacionJpaEntity::detalle).toList(),
        pagina.page(),
        pagina.size(),
        total,
        pages,
        pagina.page() == 0,
        pagina.page() >= pages - 1);
  }

  @Transactional
  public NotificacionDetalle reintentar(UUID id, Instant ahora) {
    var e = bloquear(id);
    if (e.getEvento() == com.subocol.taller.agenda.domain.model.EventoNotificacion.AGENDADA) {
      var cita =
          em.find(
              com.subocol.taller.agenda.infrastructure.persistence.entity.CitaJpaEntity.class,
              e.getCitaId());
      if (cita.getEstado() == com.subocol.taller.agenda.domain.model.EstadoCita.CANCELADA)
        throw new com.subocol.taller.agenda.domain.exception.ReglaNegocioException(
            "No se reintenta el aviso de agendamiento de una cita cancelada");
    }
    e.reintentar(ahora);
    return e.detalle();
  }
}
