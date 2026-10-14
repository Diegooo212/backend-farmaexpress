package cl.farmaexpress.payments.service;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import cl.farmaexpress.payments.domain.MetodoPago;
import cl.farmaexpress.payments.domain.Pago;
import cl.farmaexpress.payments.domain.PagoRepository;
import cl.farmaexpress.payments.eventos.Eventos.PagoAprobado;
import cl.farmaexpress.payments.eventos.Eventos.PagoRechazado;
import cl.farmaexpress.payments.proveedores.ProveedorNoDisponibleException;
import cl.farmaexpress.payments.proveedores.ProveedorPago.Redireccion;
import cl.farmaexpress.payments.proveedores.ProveedorPago.Resultado;
import cl.farmaexpress.payments.web.dto.PagoDtos.IniciarPagoRequest;
import cl.farmaexpress.payments.web.dto.PagoDtos.MetodoResponse;
import cl.farmaexpress.payments.web.dto.PagoDtos.PagoIniciadoResponse;
import cl.farmaexpress.payments.web.dto.PagoDtos.PagoResponse;
import cl.farmaexpress.payments.web.dto.PagoDtos.RedireccionResponse;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

/**
 * Orquesta un pago: lo registra, lo inicia en el proveedor elegido (a través del circuit breaker),
 * procesa el regreso del cliente confirmando con el proveedor y publica el resultado en RabbitMQ.
 * Las llamadas al proveedor se hacen fuera de las transacciones de base de datos.
 */
@Service
public class PagoService {

	private static final Logger log = LoggerFactory.getLogger(PagoService.class);

	private final PagoRepository pagos;
	private final PasarelaPagos pasarela;
	private final SimulacionFallas fallas;
	private final ApplicationEventPublisher eventos;
	private final TransactionTemplate transaccion;
	private final Clock reloj = Clock.systemUTC();
	private final String urlRetorno;
	private final String urlFrontend;

	public PagoService(PagoRepository pagos, PasarelaPagos pasarela, SimulacionFallas fallas,
			ApplicationEventPublisher eventos, TransactionTemplate transaccion,
			@Value("${farmaexpress.pagos.url-retorno}") String urlRetorno,
			@Value("${farmaexpress.pagos.url-frontend}") String urlFrontend) {
		this.pagos = pagos;
		this.pasarela = pasarela;
		this.fallas = fallas;
		this.eventos = eventos;
		this.transaccion = transaccion;
		this.urlRetorno = urlRetorno;
		this.urlFrontend = urlFrontend;
	}

	public List<MetodoResponse> metodos() {
		return Arrays.stream(MetodoPago.values())
			.map(m -> new MetodoResponse(m.name(), m.nombre(), m.descripcion(), pasarela.modo(m).name(),
					pasarela.disponible(m), pasarela.estado(m).name(), fallas.estaCaido(m)))
			.toList();
	}

	public List<MetodoResponse> simularFalla(MetodoPago metodo, boolean caido) {
		fallas.cambiar(metodo, caido);
		log.warn("Simulación: {} queda {}", metodo.nombre(), caido ? "CAÍDO" : "operativo");
		return metodos();
	}

	/**
	 * Crea el pago y lo inicia en el proveedor. Si el proveedor no está disponible (no responde o su
	 * circuit breaker está abierto) responde 503 y el pedido sigue esperando pago con otro medio.
	 */
	public PagoIniciadoResponse iniciar(IniciarPagoRequest request, Usuario usuario) {
		if (pagos.existsByPedidoIdAndEstado(request.pedidoId(), Pago.Estado.APROBADO)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Este pedido ya está pagado.");
		}
		MetodoPago metodo = request.metodo();
		Pago pago = pagos.save(new Pago(request.pedidoId(), request.reservaId(), usuario.id(), request.monto(), metodo,
				pasarela.modo(metodo), UUID.randomUUID().toString(), reloj.instant()));
		String descripcion = request.descripcion() != null && !request.descripcion().isBlank() ? request.descripcion()
				: "Pedido FarmaExpress #" + request.pedidoId();
		String retorno = urlRetorno + "/" + metodo.ruta() + "?pago=" + pago.getId();

		try {
			Redireccion redireccion = pasarela.iniciar(pago, descripcion, retorno);
			pago.iniciado(redireccion.tokenProveedor(), reloj.instant());
			pago = pagos.save(pago);
			return new PagoIniciadoResponse(pago.getId(), pago.getPedidoId(), pago.getEstado().name(), metodo.name(),
					pago.getModo().name(),
					new RedireccionResponse(redireccion.metodoHttp(), redireccion.url(), redireccion.campos()));
		}
		catch (CallNotPermittedException ex) {
			throw fallo(pago, metodo.nombre() + " no está disponible en este momento. Elige otro medio de pago.",
					"Circuit breaker abierto");
		}
		catch (ProveedorNoDisponibleException ex) {
			log.warn("{} falló al iniciar el pago {}: {}", metodo.nombre(), pago.getId(), ex.getMessage());
			throw fallo(pago, metodo.nombre() + " no respondió. Elige otro medio de pago o inténtalo en unos minutos.",
					ex.getMessage());
		}
		catch (RestClientResponseException ex) {
			log.error("{} rechazó la solicitud del pago {}: {} {}", metodo.nombre(), pago.getId(), ex.getStatusCode(),
					ex.getResponseBodyAsString());
			throw fallo(pago, metodo.nombre() + " rechazó la solicitud de pago. Elige otro medio de pago.",
					"Respuesta " + ex.getStatusCode());
		}
	}

	/**
	 * El cliente volvió desde el proveedor. Se confirma el resultado con el proveedor y se devuelve la
	 * página del frontend a la que hay que redirigir su navegador.
	 */
	public String procesarRetorno(MetodoPago metodo, Map<String, String> parametros) {
		Long pagoId = parsearId(parametros.get("pago"));
		Pago pago = pagoId == null ? null : pagos.findById(pagoId).orElse(null);
		if (pago == null || pago.getMetodo() != metodo) {
			log.warn("Retorno de {} con un pago desconocido: {}", metodo.nombre(), parametros.get("pago"));
			return urlFrontend + "/pago/resultado?error=pago-desconocido";
		}
		String destino = urlFrontend + "/pago/resultado?pedido=" + pago.getPedidoId();
		if (pago.getEstado().esFinal()) {
			return destino; // Recarga o doble envío del formulario: ya se procesó.
		}

		Resultado resultado;
		try {
			resultado = pasarela.confirmar(pago, parametros);
		}
		catch (IllegalArgumentException ex) {
			// Parámetros que no corresponden al pago (token equivocado): no se toca el pago.
			log.warn("Retorno inválido para el pago {}: {}", pago.getId(), ex.getMessage());
			return destino + "&error=verificacion";
		}
		catch (CallNotPermittedException | ProveedorNoDisponibleException ex) {
			resultado = Resultado.rechazado("No pudimos confirmar el pago con " + metodo.nombre()
					+ ". No se hizo ningún cobro; puedes intentarlo con otro medio de pago.");
		}

		if (resultado.pendiente()) {
			return destino + "&pendiente=1";
		}
		Resultado finalizado = resultado;
		try {
			transaccion.executeWithoutResult(estado -> finalizar(pagoId, finalizado));
		}
		catch (ObjectOptimisticLockingFailureException ex) {
			log.info("El pago {} se procesó en paralelo; se usa el primer resultado", pagoId);
		}
		return destino;
	}

	public PagoResponse obtener(Long id, Usuario usuario) {
		Pago pago = pagos.findById(id).orElseThrow(() -> noEncontrado(id));
		if (!usuario.esPersonalFarmacia() && !pago.perteneceA(usuario.id())) {
			throw noEncontrado(id);
		}
		return PagoResponse.from(pago);
	}

	public List<PagoResponse> delPedido(Long pedidoId, Usuario usuario) {
		return pagos.findAllByPedidoIdOrderByIdDesc(pedidoId).stream()
			.filter(p -> usuario.esPersonalFarmacia() || p.perteneceA(usuario.id()))
			.map(PagoResponse::from)
			.toList();
	}

	/** Guarda el resultado y publica el evento (se envía a RabbitMQ al hacer commit). */
	private void finalizar(Long pagoId, Resultado resultado) {
		Pago pago = pagos.findById(pagoId).orElseThrow();
		if (pago.getEstado().esFinal()) {
			return;
		}
		String evento = UUID.randomUUID().toString();
		if (resultado.aprobado()) {
			pago.aprobar(resultado.referencia(), resultado.detalle(), reloj.instant());
			eventos.publishEvent(new PagoAprobado(evento, pago.getId(), pago.getPedidoId(), pago.getReservaId(),
					pago.getCuentaId(), pago.getMonto(), pago.getMetodo().name(), resultado.referencia(), reloj.instant()));
		}
		else {
			pago.rechazar(resultado.detalle(), reloj.instant());
			eventos.publishEvent(new PagoRechazado(evento, pago.getId(), pago.getPedidoId(), pago.getReservaId(),
					pago.getCuentaId(), pago.getMonto(), pago.getMetodo().name(), resultado.detalle(), reloj.instant()));
		}
		pagos.save(pago);
	}

	private ResponseStatusException fallo(Pago pago, String mensaje, String detalleInterno) {
		pago.fallar(detalleInterno, reloj.instant());
		pagos.save(pago);
		return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, mensaje);
	}

	private static Long parsearId(String valor) {
		try {
			return valor == null ? null : Long.valueOf(valor.trim());
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

	private static ResponseStatusException noEncontrado(Long id) {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el pago " + id + ".");
	}
}
