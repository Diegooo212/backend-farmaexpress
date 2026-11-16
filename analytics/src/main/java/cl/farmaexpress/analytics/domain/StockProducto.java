package cl.farmaexpress.analytics.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Stock vigente de un producto, según el último evento del tópico compactado farmaexpress.stock. */
@Entity
@Table(name = "stock_productos")
public class StockProducto {

	@Id
	@Column(name = "medicamento_id")
	private Long medicamentoId;

	@Column(name = "stock_por_pedido", nullable = false)
	private Integer stockPorPedido;

	@Column(name = "stock_total", nullable = false)
	private Integer stockTotal;

	@Column(name = "actualizado_en", nullable = false)
	private Instant actualizadoEn;

	@Column(nullable = false)
	private Integer particion;

	@Column(name = "offset_kafka", nullable = false)
	private Long offsetKafka;

	protected StockProducto() {
	}

	public StockProducto(Long medicamentoId) {
		this.medicamentoId = medicamentoId;
	}

	public void actualizar(int stockPorPedido, int stockTotal, Instant fecha, int particion, long offset) {
		this.stockPorPedido = stockPorPedido;
		this.stockTotal = stockTotal;
		this.actualizadoEn = fecha;
		this.particion = particion;
		this.offsetKafka = offset;
	}

	/** Un evento más antiguo que el vigente (llegó tarde) no debe pisar el valor actual. */
	public boolean esMasNuevoQue(Instant fecha) {
		return actualizadoEn != null && actualizadoEn.isAfter(fecha);
	}

	public Long getMedicamentoId() {
		return medicamentoId;
	}

	public Integer getStockPorPedido() {
		return stockPorPedido;
	}

	public Integer getStockTotal() {
		return stockTotal;
	}

	public Instant getActualizadoEn() {
		return actualizadoEn;
	}
}
