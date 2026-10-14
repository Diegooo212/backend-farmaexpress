package cl.farmaexpress.inventory.web;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import cl.farmaexpress.inventory.service.InventarioService;
import cl.farmaexpress.inventory.service.Usuario;
import cl.farmaexpress.inventory.web.dto.InventarioDtos.CoberturaRequest;
import cl.farmaexpress.inventory.web.dto.InventarioDtos.CoberturaResponse;
import cl.farmaexpress.inventory.web.dto.InventarioDtos.DisponibilidadResponse;
import cl.farmaexpress.inventory.web.dto.InventarioDtos.LoteRequest;
import cl.farmaexpress.inventory.web.dto.InventarioDtos.LoteResponse;
import cl.farmaexpress.inventory.web.dto.InventarioDtos.MermaRequest;
import cl.farmaexpress.inventory.web.dto.InventarioDtos.MovimientoResponse;
import cl.farmaexpress.inventory.web.dto.InventarioDtos.ReservaRequest;
import cl.farmaexpress.inventory.web.dto.InventarioDtos.ReservaResponse;
import cl.farmaexpress.inventory.web.dto.InventarioDtos.ResumenResponse;
import cl.farmaexpress.inventory.web.dto.InventarioDtos.SucursalResponse;

@RestController
@RequestMapping("/api/inventory")
public class InventarioController {

	private final InventarioService inventario;

	public InventarioController(InventarioService inventario) {
		this.inventario = inventario;
	}

	// Públicos: los usa la tienda antes de iniciar sesión.

	@GetMapping("/sucursales")
	public List<SucursalResponse> sucursales() {
		return inventario.sucursales();
	}

	@GetMapping("/disponibilidad/{medicamentoId}")
	public List<DisponibilidadResponse> disponibilidad(@PathVariable Long medicamentoId) {
		return inventario.disponibilidad(medicamentoId);
	}

	@PostMapping("/cobertura")
	public List<CoberturaResponse> cobertura(@Valid @RequestBody CoberturaRequest request) {
		return inventario.cobertura(request.items());
	}

	// Reservas: las pide el BFF con el token del cliente durante el checkout.

	@PostMapping("/reservas")
	@ResponseStatus(HttpStatus.CREATED)
	public ReservaResponse reservar(@Valid @RequestBody ReservaRequest request, @AuthenticationPrincipal Jwt jwt) {
		return inventario.reservar(request, Usuario.desde(jwt));
	}

	@GetMapping("/reservas/{id}")
	public ReservaResponse reserva(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
		return inventario.obtenerReserva(id, Usuario.desde(jwt));
	}

	@DeleteMapping("/reservas/{id}")
	public ReservaResponse liberar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
		return inventario.liberar(id, Usuario.desde(jwt));
	}

	// Gestión de lotes: solo personal de farmacia (Operador o Administrador).

	@GetMapping("/lotes")
	public List<LoteResponse> lotes(@RequestParam(required = false) Long sucursalId,
			@RequestParam(required = false) Long medicamentoId, @RequestParam(required = false) String estado) {
		return inventario.lotes(sucursalId, medicamentoId, estado);
	}

	@PostMapping("/lotes")
	@ResponseStatus(HttpStatus.CREATED)
	public LoteResponse ingresar(@Valid @RequestBody LoteRequest request, @AuthenticationPrincipal Jwt jwt) {
		return inventario.ingresar(request, Usuario.desde(jwt));
	}

	@PostMapping("/lotes/{id}/merma")
	public LoteResponse darDeBaja(@PathVariable Long id, @Valid @RequestBody MermaRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return inventario.darDeBaja(id, request, Usuario.desde(jwt));
	}

	@GetMapping("/lotes/{id}/movimientos")
	public List<MovimientoResponse> movimientos(@PathVariable Long id) {
		return inventario.movimientos(id);
	}

	@GetMapping("/resumen")
	public ResumenResponse resumen() {
		return inventario.resumen();
	}
}
