# Notificaciones de citas: local y cloud

## Qué hace

Al agendar y al cancelar MANUALMENTE una cita, Agenda guarda un registro PENDIENTE en notificacion_cita dentro de la misma transacción que la cita. Un procesador de un solo hilo lo reclama y lo envía después del commit. Si falla el guardado, no quedan ni cita ni aviso parcial. El envío de red no mantiene el bloqueo del cupo ni una conexión JDBC ocupada durante toda la llamada.

El modo LOG cumple la simulación del enunciado y registra WHATSAPP_SIMULADO con acción, cita, orden, tipo, fecha y asesor. No consulta el teléfono ni llama a Meta. El modo WHATSAPP usa el endpoint oficial /{version}/{phoneId}/messages, con token Bearer y plantillas.

La cancelación automática al cerrar una orden sigue pendiente. Cuando se implemente, deberá omitir el aviso; no debe reutilizar sin cambios la operación de cancelación manual que encola CANCELADA_MANUAL.

## Archivos de configuración

| Archivo | Uso |
|---|---|
| .env.local.example | Plantilla pública para desarrollo |
| .env.local | Copia privada local, ignorada por Git; creada inicialmente en modo LOG, sin token |
| .env.cloud.example | Variables para el servicio Agenda en cloud |
| application-local.properties | Conexiones locales y lectura opcional de .env.local |
| application-cloud.properties | Credenciales externas obligatorias; nunca importa .env.local |

Los archivos .env reales no se incluyen en las imágenes Docker. Tampoco debes subirlos a Git. Los ejemplos contienen únicamente valores de desarrollo o campos vacíos. Los secretos de nube se cargan en las variables de cada servicio del proveedor.

## Ejecutar localmente

Los comandos Docker Compose se ejecutan desde el repositorio hermano `subocol-infra`. Para Maven, el archivo privado `.env.local` se coloca dentro de `agenda-service`. Son archivos independientes; configurar el correspondiente al modo de ejecucion.

Sin credenciales, docker compose up --build usa LOG. Para que Compose lea tus cambios de .env.local:

```powershell
docker compose --env-file .env.local up --build
```

Con Maven, ejecuta mvn spring-boot:run dentro de cada carpeta de servicio. El perfil local importa automáticamente el .env.local de la carpeta agenda-service. No coloques comillas alrededor de los valores: el archivo también se interpreta como properties. Las variables del proceso prevalecen sobre el archivo; si antes exportaste una variable en la terminal, revisa ese valor al cambiar de modo.

Para probar WhatsApp real, edita PRIVADAMENTE .env.local:

```properties
NOTIFICATIONS_MODE=WHATSAPP
WHATSAPP_API_VERSION=v25.0
WHATSAPP_PHONE_NUMBER_ID=IDENTIFICADOR_DEL_NUMERO_DE_PRUEBA
WHATSAPP_ACCESS_TOKEN=TOKEN_PRIVADO_GENERADO_EN_META
WHATSAPP_TEST_RECIPIENT=TU_NUMERO_VERIFICADO_CON_INDICATIVO
WHATSAPP_TEMPLATE_SCHEDULED=hello_world
WHATSAPP_TEMPLATE_SCHEDULED_LANGUAGE=en_US
WHATSAPP_TEMPLATE_SCHEDULED_PARAMS=
WHATSAPP_TEMPLATE_CANCELLED=hello_world
WHATSAPP_TEMPLATE_CANCELLED_LANGUAGE=en_US
WHATSAPP_TEMPLATE_CANCELLED_PARAMS=
```

Los textos en mayúsculas son marcadores: reemplázalos, sin copiarlos como valores reales. Phone Number ID es el identificador, no el número visible ni el ID de cuenta WABA. Usa una versión vigente para tu aplicación; puedes mantener v25.0 o ajustar la versión según el ejemplo de Meta.

El destinatario de prueba se escribe con indicativo y solo dígitos, por ejemplo 573001234567. Debe ser TU número permitido por Meta; el ejemplo es ficticio. Esta variable redirige TODOS los avisos a ese número: úsala solo para demostración. Si queda vacía, el adaptador consulta el teléfono real de la orden mediante el endpoint interno autenticado. Los números locales de 10 dígitos reciben el indicativo configurado en WHATSAPP_DEFAULT_COUNTRY_CODE (57 por defecto).

Reinicia Agenda después de cambiar credenciales o modo. El token temporal puede vencer; una respuesta 401 deja el aviso FALLIDA. Corrige el token y solicita el reintento manual del aviso; la cita sigue guardada.

## hello_world y mensajes de negocio

El JSON que probó el candidato usa hello_world, en_US y cero parámetros. Esta plantilla sirve para demostrar conectividad: su texto es genérico, tanto al agendar como al cancelar. No se presenta como una confirmación detallada de cita.

Para avisos reales de negocio configura plantillas aprobadas diferentes, por ejemplo cita_agendada y cita_cancelada. La aplicación soporta parámetros posicionales de texto en el BODY, con este catálogo: CITA_ID, ORDEN_ID, FECHA, HORA, TIPO_CITA, EVENTO y ASESOR. FECHA/HORA usan WHATSAPP_TIME_ZONE (America/Bogota por defecto).

Ejemplo para una plantilla aprobada cuyo cuerpo tiene tres parámetros, en ese orden:

```properties
WHATSAPP_TEMPLATE_SCHEDULED=cita_agendada
WHATSAPP_TEMPLATE_SCHEDULED_LANGUAGE=es
WHATSAPP_TEMPLATE_SCHEDULED_PARAMS=CITA_ID,FECHA,HORA
WHATSAPP_TEMPLATE_CANCELLED=cita_cancelada
WHATSAPP_TEMPLATE_CANCELLED_LANGUAGE=es
WHATSAPP_TEMPLATE_CANCELLED_PARAMS=CITA_ID,FECHA,HORA
```

Los nombres, idioma y cantidad/orden de parámetros deben coincidir con Meta. No se crean ni aprueban plantillas desde el backend. Componentes multimedia, parámetros con nombre, encabezados y botones no están implementados. Contrato basado en la [colección oficial de Meta](https://www.postman.com/meta/whatsapp-business-platform/documentation/wlk6lh4/whatsapp-cloud-api?entity=folder-08213e00-c6d0-48a2-8be8-62247b8d29bd).

## Cloud

Carga .env.cloud.example como referencia en las variables de AGENDA y ../ordenes-service/.env.cloud.example en ÓRDENES, reemplazando cada marcador. No copies las conexiones locales. Ambos requieren SPRING_PROFILES_ACTIVE=cloud, sus conexiones MySQL independientes, credenciales de aplicación/migración y autenticación. Agenda requiere ORDENES_URL y las variables de notificaciones.

También en cloud el modo predeterminado es LOG. Activa WHATSAPP de forma explícita. Para clientes reales deja WHATSAPP_TEST_RECIPIENT vacío, usa plantillas apropiadas y credenciales válidas; el entorno gratuito de prueba no equivale a producción ilimitada gratis. No se ha desplegado esta versión ni se han enviado mensajes reales desde las herramientas de implementación.

## Reintentos y consulta

Por defecto: consulta cada 30 segundos, lotes de hasta 5 avisos, máximo 3 intentos automáticos por ciclo, reserva de 60 segundos y espera inicial de reintento de 30 segundos con crecimiento exponencial hasta 1 hora. Los intentos incluyen reclamaciones interrumpidas. El máximo evita envíos ilimitados; los parámetros están en los ejemplos de entorno.

Estados:

- PENDIENTE: guardado, espera despacho o reintento.
- EN_PROCESO: reservado por un procesador.
- SIMULADA: se escribió el log; no hubo envío real.
- ACEPTADA: Meta devolvió un identificador de mensaje. NO confirma que el destinatario lo recibió.
- FALLIDA: error permanente o agotamiento de intentos.

Se reintentan errores de conexión, HTTP 408/429 y errores HTTP 5xx. Los demás HTTP 4xx requieren corregir configuración/datos y reintentar manualmente. No se guardan ni se devuelven cuerpos de error remotos, tokens ni números destinatarios en la consulta de notificaciones.

Consultar, con las mismas credenciales Basic de Agenda:

```text
GET /api/notificaciones/listar?citaId=UUID&estado=PENDIENTE&page=0&size=20
PATCH /api/notificaciones/{id}/reintentar
```

PATCH no lleva body y solo permite FALLIDA; responde {status:200,data:{update:true,...}}. Reinicia el contador de ese ciclo. No reenvía registros ACEPTADA/SIMULADA ni permite reintentar un aviso de agendamiento si la cita ya fue cancelada. Ambos endpoints están en la colección Postman actualizada.

## Patrones y límites

- Outbox transaccional: AgendaPersistenceAdapter y NotificacionJpaEntity guardan cita y aviso juntos.
- Puertos/adaptadores: NotificacionCitaPort permite intercambiar NotificacionLogAdapter y WhatsAppCloudAdapter.
- Reserva con vencimiento y token: BuzonNotificacionesAdapter evita que dos procesos activos reclamen simultáneamente el mismo aviso; un token antiguo no confirma una reserva nueva.
- Reintentos acotados: DespacharNotificacionesUseCase aplica el presupuesto y las esperas, sin un broker o servicio adicional.

La coordinación usa JPA/JPQL y bloqueos de base de datos, sin synchronized ni consultas nativas. Los registros anteriores al nuevo buzón no generan mensajes retroactivos.

El procesamiento conserva pendientes tras reiniciar. No ofrece exactamente-una-vez: si Meta acepta y se pierde la respuesta o falla la confirmación local, un reintento puede duplicar el mensaje. La recepción/lectura requeriría webhooks, pendientes en esta etapa. No se añade un webhook público sin verificación.
