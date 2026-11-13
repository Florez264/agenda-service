CREATE TABLE franja (
 id CHAR(36) NOT NULL PRIMARY KEY,asesor VARCHAR(100) NOT NULL,
 inicio TIMESTAMP(6) NOT NULL,fin TIMESTAMP(6) NOT NULL,cupo INT NOT NULL,
 CONSTRAINT uq_asesor_inicio UNIQUE(asesor,inicio),CONSTRAINT ck_franja_cupo CHECK(cupo BETWEEN 1 AND 10),CONSTRAINT ck_franja_fechas CHECK(fin>inicio)
);
CREATE INDEX idx_franja_inicio ON franja(inicio,id);
CREATE TABLE cita (
 id CHAR(36) NOT NULL PRIMARY KEY,orden_id CHAR(36) NOT NULL,franja_id CHAR(36) NOT NULL,
 tipo VARCHAR(20) NOT NULL,estado VARCHAR(20) NOT NULL,creada_por VARCHAR(100) NOT NULL,
 fecha_creacion TIMESTAMP(6) NOT NULL,motivo_cancelacion VARCHAR(500),cancelada_por VARCHAR(100),fecha_cancelacion TIMESTAMP(6),version BIGINT NOT NULL,
 CONSTRAINT fk_cita_franja FOREIGN KEY(franja_id) REFERENCES franja(id)
);
CREATE INDEX idx_cita_franja_estado ON cita(franja_id,estado);
CREATE INDEX idx_cita_orden ON cita(orden_id,fecha_creacion);
