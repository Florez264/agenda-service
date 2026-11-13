CREATE TABLE notificacion_cita (
 id CHAR(36) NOT NULL PRIMARY KEY,cita_id CHAR(36) NOT NULL,orden_id CHAR(36) NOT NULL,
 evento VARCHAR(30) NOT NULL,orden_evento INT NOT NULL,tipo_cita VARCHAR(20) NOT NULL,
 asesor VARCHAR(100) NOT NULL,inicio TIMESTAMP(6) NOT NULL,
 estado VARCHAR(20) NOT NULL,intentos INT NOT NULL,
 creada TIMESTAMP(6) NOT NULL,siguiente_intento TIMESTAMP(6) NOT NULL,
 reserva_hasta TIMESTAMP(6),reserva_token CHAR(36),codigo_error VARCHAR(60),mensaje_proveedor_id VARCHAR(300),version BIGINT NOT NULL,
 CONSTRAINT fk_notificacion_cita FOREIGN KEY(cita_id) REFERENCES cita(id),
 CONSTRAINT uq_cita_evento UNIQUE(cita_id,evento)
);
CREATE INDEX idx_notificacion_despacho ON notificacion_cita(estado,siguiente_intento,creada);
CREATE INDEX idx_notificacion_reserva ON notificacion_cita(estado,reserva_hasta);
