package cl.farmaexpress.bff.eventos;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import cl.farmaexpress.bff.pedidos.Pedido;

/**
 * Evento publicado en Kafka ({@code farmaexpress.ventas}, clave = id del pedido) cuando el pago de un
 * pedido queda aprobado. Lleva lo necesario para la analítica de ventas y nada del cliente: ni su nombre,
 * ni su correo, ni su dirección.
 *
 * @param version versión del esquema: si se agrega un campo, los consumidores actuales lo ignoran
 * @param fecha momento en que se aprobó el pago
 * @param entrega RETIRO en farmacia o DESPACHO a domicilio
 * @param sucursalNombre farmacia de retiro, o null si sale del almacén central
 * @param total total pagado = suma de los productos + costo de despacho
 */
public record VentaRegistrada(String eventoId, int version, Long pedidoId, Instant fecha, String entrega, Long sucursalId,
		String sucursalNombre, String metodoPago, int costoDespacho, int total, List<Item> items) {

	public static final int VERSION = 1;

	public record Item(Long medicamentoId, String nombre, int cantidad, int precioUnitario) {
	}

	public static VentaRegistrada de(Pedido pedido, Instant fecha) {
		List<Item> items = pedido.getItems().stream()
			.map(i -> new Item(i.getMedicamentoId(), i.getNombre(), i.getCantidad(), i.getPrecioUnitario()))
			.toList();
		return new VentaRegistrada(UUID.randomUUID().toString(), VERSION, pedido.getId(), fecha,
				pedido.getEntrega() == null ? Pedido.Entrega.RETIRO.name() : pedido.getEntrega().name(), pedido.getSucursalId(),
				pedido.getSucursalNombre(), pedido.getMetodoPago(),
				pedido.getCostoDespacho() == null ? 0 : pedido.getCostoDespacho(), pedido.getTotal(), items);
	}
}
