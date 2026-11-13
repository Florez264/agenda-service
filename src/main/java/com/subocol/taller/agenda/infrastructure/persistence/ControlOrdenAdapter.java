package com.subocol.taller.agenda.infrastructure.persistence;

import com.subocol.taller.agenda.infrastructure.persistence.entity.ControlOrdenJpaEntity;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

@Repository
@RequiredArgsConstructor
public class ControlOrdenAdapter {
  private final EntityManager em;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void asegurar(UUID id) {
    if (em.find(ControlOrdenJpaEntity.class, id) == null) {
      em.persist(ControlOrdenJpaEntity.crear(id));
      em.flush();
    }
  }
}
