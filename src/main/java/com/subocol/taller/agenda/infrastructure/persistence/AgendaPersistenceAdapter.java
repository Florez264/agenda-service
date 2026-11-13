package com.subocol.taller.agenda.infrastructure.persistence;

import com.subocol.taller.agenda.application.dto.*;
import com.subocol.taller.agenda.application.port.AgendaPort;
import com.subocol.taller.agenda.domain.exception.*;
import com.subocol.taller.agenda.domain.model.*;
import com.subocol.taller.agenda.infrastructure.persistence.entity.*;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
public class AgendaPersistenceAdapter implements AgendaPort {
  private final EntityManager em;
  private final Clock clock;
  private final ControlOrdenAdapter controles;

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public void prepararOrden(UUID id) {
    try {
      controles.asegurar(id);
    } catch (org.springframework.dao.DataIntegrityViolationException concurrente) {
      // Another transaction created the same order guard. Read the committed row below.
    }
  }

  private ControlOrdenJpaEntity controlar(UUID id) {
    var control = em.find(ControlOrdenJpaEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
    if (control == null) throw new ReglaNegocioException("Reintenta la operación de agenda");
    return control;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void cancelarPorCierre(UUID ordenId, long version) {
    var control = controlar(ordenId);
    // Order guard -> slot locks in stable order: same ordering as booking.
    var ids =
        em.createQuery(
                "select distinct c.franja.id from CitaJpaEntity c where c.ordenId=:orden and"
                    + " c.estado=:estado and c.ordenVersion<:version order by c.franja.id",
                UUID.class)
            .setParameter("orden", ordenId)
            .setParameter("estado", EstadoCita.RESERVADA)
            .setParameter("version", version)
            .getResultList();
    for (var id : ids) bloquear(id);
    var citas =
        em.createQuery(
                "select c from CitaJpaEntity c where c.ordenId=:orden and c.estado=:estado and"
                    + " c.ordenVersion<:version",
                CitaJpaEntity.class)
            .setParameter("orden", ordenId)
            .setParameter("estado", EstadoCita.RESERVADA)
            .setParameter("version", version)
            .getResultList();
    for (var c : citas)
      c.aplicarCancelacion(
          c.aDominio()
              .cancelar(
                  "Cancelación automática por cierre de orden", "sistema-cierre", clock.instant()));
    control.registrar(version);
    em.flush();
    // Deliberately no notification event: this is not a manual cancellation.
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Franja crearFranja(Franja f) {
    em.persist(FranjaJpaEntity.desde(f));
    em.flush();
    return f;
  }

  private FranjaJpaEntity bloquear(UUID id) {
    var f =
        em.find(
            FranjaJpaEntity.class,
            id,
            LockModeType.PESSIMISTIC_WRITE,
            Map.of("jakarta.persistence.lock.timeout", 3000));
    if (f == null) throw new RecursoNoEncontradoException("Franja no encontrada");
    return f;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CitaDetalle reservar(Cita cita, long ordenVersion) {
    var control = controlar(cita.getOrdenId());
    if (ordenVersion <= control.getUltimoCierre())
      throw new ReglaNegocioException("La orden fue cerrada; actualiza la orden antes de agendar");
    var franja = bloquear(cita.getFranjaId());
    long ocupados =
        em.createQuery(
                "select count(c) from CitaJpaEntity c where c.franja.id=:id and c.estado=:estado",
                Long.class)
            .setParameter("id", franja.getId())
            .setParameter("estado", EstadoCita.RESERVADA)
            .getSingleResult();
    franja.aDominio().validarReserva(ocupados, clock.instant());
    var e = CitaJpaEntity.desde(cita, franja);
    e.registrarVersionOrden(ordenVersion);
    em.persist(e);
    em.flush();
    var detalle = detalle(e);
    em.persist(
        NotificacionJpaEntity.pendiente(detalle, EventoNotificacion.AGENDADA, clock.instant()));
    em.flush();
    return detalle;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CitaDetalle cancelar(UUID id, String motivo, String actor) {
    // Fetch only the immutable slot ID first. All mutations take the slot lock before reading the
    // appointment.
    var ids =
        em.createQuery("select c.franja.id from CitaJpaEntity c where c.id=:id", UUID.class)
            .setParameter("id", id)
            .getResultList();
    if (ids.isEmpty()) throw new RecursoNoEncontradoException("Cita no encontrada");
    var franja = bloquear(ids.getFirst());
    var e = em.find(CitaJpaEntity.class, id);
    if (!franja.getInicio().isAfter(clock.instant()))
      throw new ReglaNegocioException("No se puede cancelar una cita cuya franja ya inició");
    e.aplicarCancelacion(e.aDominio().cancelar(motivo, actor, clock.instant()));
    em.flush();
    var detalle = detalle(e);
    em.persist(
        NotificacionJpaEntity.pendiente(
            detalle, EventoNotificacion.CANCELADA_MANUAL, clock.instant()));
    em.flush();
    return detalle;
  }

  public CitaDetalle consultar(UUID id) {
    var e =
        em.createQuery(
                "select c from CitaJpaEntity c join fetch c.franja where c.id=:id",
                CitaJpaEntity.class)
            .setParameter("id", id)
            .getResultStream()
            .findFirst()
            .orElseThrow(() -> new RecursoNoEncontradoException("Cita no encontrada"));
    return detalle(e);
  }

  public Pagina<CitaDetalle> listar(
      UUID ordenId, String asesor, EstadoCita estado, Paginacion pagina) {
    String filtro =
        " where (:orden is null or c.ordenId=:orden) and (:asesor is null or"
            + " c.franja.asesor=:asesor) and (:estado is null or c.estado=:estado)";
    var query =
        em.createQuery(
                "select c from CitaJpaEntity c join fetch c.franja"
                    + filtro
                    + " order by c.franja.inicio asc,c.id asc",
                CitaJpaEntity.class)
            .setParameter("orden", ordenId)
            .setParameter("asesor", asesor)
            .setParameter("estado", estado)
            .setFirstResult(pagina.page() * pagina.size())
            .setMaxResults(pagina.size());
    long total =
        em.createQuery("select count(c) from CitaJpaEntity c" + filtro, Long.class)
            .setParameter("orden", ordenId)
            .setParameter("asesor", asesor)
            .setParameter("estado", estado)
            .getSingleResult();
    return pagina(query.getResultList().stream().map(this::detalle).toList(), total, pagina);
  }

  public Pagina<FranjaDisponible> listarFranjas(
      String asesor, Instant desde, Instant hasta, Paginacion pagina) {
    String filtro =
        " where (:asesor is null or f.asesor=:asesor) and f.inicio>=:desde and f.inicio<:hasta";
    var franjas =
        em.createQuery(
                "select f from FranjaJpaEntity f" + filtro + " order by f.inicio asc,f.id asc",
                FranjaJpaEntity.class)
            .setParameter("asesor", asesor)
            .setParameter("desde", desde)
            .setParameter("hasta", hasta)
            .setFirstResult(pagina.page() * pagina.size())
            .setMaxResults(pagina.size())
            .getResultList();
    long total =
        em.createQuery("select count(f) from FranjaJpaEntity f" + filtro, Long.class)
            .setParameter("asesor", asesor)
            .setParameter("desde", desde)
            .setParameter("hasta", hasta)
            .getSingleResult();
    Map<UUID, Long> ocupados = new HashMap<>();
    if (!franjas.isEmpty()) {
      var ids = franjas.stream().map(FranjaJpaEntity::getId).toList();
      em.createQuery(
              "select c.franja.id,count(c) from CitaJpaEntity c where c.franja.id in :ids and"
                  + " c.estado=:estado group by c.franja.id",
              Object[].class)
          .setParameter("ids", ids)
          .setParameter("estado", EstadoCita.RESERVADA)
          .getResultList()
          .forEach(row -> ocupados.put((UUID) row[0], (Long) row[1]));
    }
    var filas =
        franjas.stream()
            .map(
                f -> {
                  long n = ocupados.getOrDefault(f.getId(), 0L);
                  return new FranjaDisponible(
                      f.getId(),
                      f.getAsesor(),
                      f.getInicio(),
                      f.getFin(),
                      f.getCupo(),
                      n,
                      f.getCupo() - n);
                })
            .toList();
    return pagina(filas, total, pagina);
  }

  private CitaDetalle detalle(CitaJpaEntity c) {
    return CitaDetalle.desde(c.aDominio(), c.getFranja().aDominio());
  }

  private <T> Pagina<T> pagina(List<T> filas, long total, Paginacion p) {
    int pages = (int) ((total + p.size() - 1) / p.size());
    return new Pagina<>(
        filas, p.page(), p.size(), total, pages, p.page() == 0, p.page() >= pages - 1);
  }
}
