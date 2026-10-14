package cl.farmaexpress.rabbitadmin.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import cl.farmaexpress.rabbitadmin.config.RabbitAdminProperties;
import cl.farmaexpress.rabbitadmin.web.dto.RabbitDtos.BindingRequest;
import cl.farmaexpress.rabbitadmin.web.dto.RabbitDtos.BindingResponse;
import cl.farmaexpress.rabbitadmin.web.dto.RabbitDtos.ColaCreada;
import cl.farmaexpress.rabbitadmin.web.dto.RabbitDtos.ColaRequest;
import cl.farmaexpress.rabbitadmin.web.dto.RabbitDtos.ColaResponse;
import cl.farmaexpress.rabbitadmin.web.dto.RabbitDtos.ExchangeRequest;
import cl.farmaexpress.rabbitadmin.web.dto.RabbitDtos.ExchangeResponse;
import cl.farmaexpress.rabbitadmin.web.dto.RabbitDtos.TipoCola;
import cl.farmaexpress.rabbitadmin.web.dto.RabbitDtos.TipoExchange;

/**
 * Toda la administración de RabbitMQ en un solo lugar. El controlador solo llama a estos métodos
 * de alto nivel y no conoce AMQP, argumentos x-* ni la API HTTP de RabbitMQ.
 * <p>
 * Reglas:
 * <ul>
 * <li>los recursos del sistema (prefijos en {@code farmaexpress.rabbit.protegidos}) no se tocan;</li>
 * <li>no se crea algo que ya existe ni se elimina algo que no existe (409 / 404);</li>
 * <li>una cola con DLQ se conecta al exchange de fallidos con routing key "&lt;cola&gt;.dlq";</li>
 * <li>por defecto una cola con mensajes pendientes no se elimina (se pierde información).</li>
 * </ul>
 */
@Service
public class RabbitAdminService {

	private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);

	private final AmqpAdmin amqp;
	private final RabbitManagementClient management;
	private final RabbitAdminProperties props;

	public RabbitAdminService(AmqpAdmin amqp, RabbitManagementClient management, RabbitAdminProperties props) {
		this.amqp = amqp;
		this.management = management;
		this.props = props;
	}

	// ---------------------------------------------------------------- Colas

	public List<ColaResponse> listarColas() {
		return management.colas().stream().map(this::aCola).toList();
	}

	public ColaCreada crearCola(ColaRequest req) {
		String nombre = req.nombre();
		validarNoProtegido(nombre);
		if (colaExiste(nombre)) {
			throw RabbitAdminException.yaExiste("la cola", nombre);
		}
		if (req.tipoOPorDefecto() == TipoCola.QUORUM && !req.esDurable()) {
			throw RabbitAdminException.invalido("Una cola de quórum siempre es durable: quita \"durable\": false.");
		}

		List<String> creados = new ArrayList<>();
		QueueBuilder builder = req.esDurable() ? QueueBuilder.durable(nombre) : QueueBuilder.nonDurable(nombre);
		if (req.tipoOPorDefecto() == TipoCola.QUORUM) {
			builder.quorum();
		}
		if (req.ttlMs() != null) {
			builder.ttl(Math.toIntExact(req.ttlMs()));
		}
		if (req.maxMensajes() != null) {
			builder.maxLength(req.maxMensajes());
		}

		String dlq = req.llevaDlq() ? nombre + ".dlq" : null;
		if (dlq != null) {
			if (colaExiste(dlq)) {
				throw RabbitAdminException.yaExiste("la cola de fallidos", dlq);
			}
			declararExchangeDeFallidos();
			Queue colaDlq = QueueBuilder.durable(dlq).build();
			ejecutar(() -> amqp.declareQueue(colaDlq));
			ejecutar(() -> amqp.declareBinding(binding(props.dlx(), dlq, dlq)));
			builder.deadLetterExchange(props.dlx()).deadLetterRoutingKey(dlq);
			creados.add("cola " + dlq);
			creados.add("binding " + props.dlx() + " → " + dlq);
		}

		Queue cola = builder.build();
		ejecutar(() -> amqp.declareQueue(cola));
		creados.add(0, "cola " + nombre);
		log.info("Cola creada: {} (tipo={}, dlq={})", nombre, req.tipoOPorDefecto(), dlq);

		ColaResponse respuesta = new ColaResponse(nombre, req.tipoOPorDefecto().name().toLowerCase(), req.esDurable(), 0, 0,
				dlq, false);
		return new ColaCreada(respuesta, creados, new LinkedHashMap<>(cola.getArguments()));
	}

	/**
	 * @param forzar elimina aunque tenga mensajes pendientes.
	 * @param conDlq elimina también "&lt;nombre&gt;.dlq" si existe.
	 */
	public void eliminarCola(String nombre, boolean forzar, boolean conDlq) {
		validarNoProtegido(nombre);
		QueueInformation info = amqp.getQueueInfo(nombre);
		if (info == null) {
			throw RabbitAdminException.noExiste("la cola", nombre);
		}
		if (!forzar && info.getMessageCount() > 0) {
			throw new RabbitAdminException(HttpStatus.CONFLICT, "La cola \"" + nombre + "\" tiene " + info.getMessageCount()
					+ " mensajes pendientes. Vacíala o usa ?forzar=true para eliminarla igual.");
		}
		ejecutar(() -> amqp.deleteQueue(nombre));
		log.info("Cola eliminada: {} (mensajes descartados: {})", nombre, info.getMessageCount());
		String dlq = nombre + ".dlq";
		if (conDlq && colaExiste(dlq)) {
			ejecutar(() -> amqp.deleteQueue(dlq));
			log.info("Cola de fallidos eliminada: {}", dlq);
		}
	}

	// ---------------------------------------------------------------- Exchanges

	public List<ExchangeResponse> listarExchanges() {
		return management.exchanges().stream()
			// El exchange por defecto ("") y los amq.* son internos de RabbitMQ.
			.filter(e -> !texto(e, "name").isEmpty() && !texto(e, "name").startsWith("amq."))
			.map(e -> new ExchangeResponse(texto(e, "name"), texto(e, "type"), Boolean.TRUE.equals(e.get("durable")),
					props.esProtegido(texto(e, "name"))))
			.toList();
	}

	public ExchangeResponse crearExchange(ExchangeRequest req) {
		validarNoProtegido(req.nombre());
		if (management.exchange(req.nombre()).isPresent()) {
			throw RabbitAdminException.yaExiste("el exchange", req.nombre());
		}
		Exchange exchange = new ExchangeBuilder(req.nombre(), req.tipo().valor()).durable(req.esDurable()).build();
		ejecutar(() -> amqp.declareExchange(exchange));
		log.info("Exchange creado: {} ({})", req.nombre(), req.tipo().valor());
		return new ExchangeResponse(req.nombre(), req.tipo().valor(), req.esDurable(), false);
	}

	public void eliminarExchange(String nombre) {
		validarNoProtegido(nombre);
		if (management.exchange(nombre).isEmpty()) {
			throw RabbitAdminException.noExiste("el exchange", nombre);
		}
		ejecutar(() -> amqp.deleteExchange(nombre));
		log.info("Exchange eliminado: {}", nombre);
	}

	// ---------------------------------------------------------------- Bindings

	public List<BindingResponse> listarBindings() {
		return management.bindings().stream()
			// Los bindings implícitos del exchange por defecto no se muestran.
			.filter(b -> !texto(b, "source").isEmpty() && "queue".equals(texto(b, "destination_type")))
			.map(b -> new BindingResponse(texto(b, "source"), texto(b, "destination"), texto(b, "routing_key")))
			.toList();
	}

	public BindingResponse crearBinding(BindingRequest req) {
		validarNoProtegido(req.cola());
		Map<String, Object> exchange = management.exchange(req.exchange())
			.orElseThrow(() -> RabbitAdminException.noExiste("el exchange", req.exchange()));
		if (!colaExiste(req.cola())) {
			throw RabbitAdminException.noExiste("la cola", req.cola());
		}
		String tipo = texto(exchange, "type");
		String routingKey = req.routingKeyONada();
		if (TipoExchange.DIRECT.valor().equals(tipo) && (routingKey.contains("*") || routingKey.contains("#"))) {
			throw RabbitAdminException.invalido("Los comodines * y # solo funcionan en exchanges de tipo topic.");
		}
		if ((TipoExchange.DIRECT.valor().equals(tipo) || TipoExchange.TOPIC.valor().equals(tipo)) && routingKey.isEmpty()) {
			throw RabbitAdminException.invalido("Un exchange " + tipo + " necesita una routing key.");
		}
		boolean repetido = listarBindings().stream()
			.anyMatch(b -> b.exchange().equals(req.exchange()) && b.cola().equals(req.cola()) && b.routingKey().equals(routingKey));
		if (repetido) {
			throw RabbitAdminException.yaExiste("el binding", req.exchange() + " → " + req.cola() + " (" + routingKey + ")");
		}
		ejecutar(() -> amqp.declareBinding(binding(req.exchange(), req.cola(), routingKey)));
		log.info("Binding creado: {} → {} ({})", req.exchange(), req.cola(), routingKey);
		return new BindingResponse(req.exchange(), req.cola(), routingKey);
	}

	public void eliminarBinding(BindingRequest req) {
		validarNoProtegido(req.cola());
		String routingKey = req.routingKeyONada();
		boolean existe = listarBindings().stream()
			.anyMatch(b -> b.exchange().equals(req.exchange()) && b.cola().equals(req.cola()) && b.routingKey().equals(routingKey));
		if (!existe) {
			throw RabbitAdminException.noExiste("el binding", req.exchange() + " → " + req.cola() + " (" + routingKey + ")");
		}
		ejecutar(() -> amqp.removeBinding(binding(req.exchange(), req.cola(), routingKey)));
		log.info("Binding eliminado: {} → {} ({})", req.exchange(), req.cola(), routingKey);
	}

	// ---------------------------------------------------------------- Apoyo

	private boolean colaExiste(String nombre) {
		return amqp.getQueueInfo(nombre) != null;
	}

	private void validarNoProtegido(String nombre) {
		if (props.esProtegido(nombre)) {
			throw RabbitAdminException.protegido(nombre);
		}
	}

	/** El exchange de fallidos ya lo declaran los microservicios; se declara igual por si no existe (idempotente). */
	private void declararExchangeDeFallidos() {
		ejecutar(() -> amqp.declareExchange(new ExchangeBuilder(props.dlx(), "direct").durable(true).build()));
	}

	private static Binding binding(String exchange, String cola, String routingKey) {
		return new Binding(cola, Binding.DestinationType.QUEUE, exchange, routingKey, null);
	}

	private ColaResponse aCola(Map<String, Object> c) {
		String nombre = texto(c, "name");
		Object args = c.get("arguments");
		String dlq = null;
		if (args instanceof Map<?, ?> argumentos && argumentos.get("x-dead-letter-routing-key") != null) {
			dlq = String.valueOf(argumentos.get("x-dead-letter-routing-key"));
		}
		return new ColaResponse(nombre, texto(c, "type"), Boolean.TRUE.equals(c.get("durable")), numero(c, "messages"),
				(int) numero(c, "consumers"), dlq, props.esProtegido(nombre));
	}

	private static String texto(Map<String, Object> mapa, String clave) {
		Object valor = mapa.get(clave);
		return valor == null ? "" : valor.toString();
	}

	private static long numero(Map<String, Object> mapa, String clave) {
		return mapa.get(clave) instanceof Number n ? n.longValue() : 0;
	}

	/** Un error de RabbitMQ (conexión caída, argumentos incompatibles) se informa como 502 con su causa. */
	private static void ejecutar(Runnable operacion) {
		try {
			operacion.run();
		}
		catch (AmqpException e) {
			log.warn("RabbitMQ rechazó la operación: {}", e.getMessage());
			throw new RabbitAdminException(HttpStatus.BAD_GATEWAY, "RabbitMQ rechazó la operación: " + causa(e));
		}
	}

	private static String causa(Throwable e) {
		Throwable raiz = e;
		while (raiz.getCause() != null) {
			raiz = raiz.getCause();
		}
		return raiz.getMessage() == null ? e.getClass().getSimpleName() : raiz.getMessage();
	}
}
