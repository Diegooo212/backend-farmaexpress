package cl.farmaexpress.prescriptions.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * 401 y 403 con cuerpo JSON (Problem Details) en español, además del header estándar
 * WWW-Authenticate que indica el motivo (invalid_token, insufficient_scope...).
 */
public final class RespuestasSeguridad {

	private RespuestasSeguridad() {
	}

	/** Sin token, token vencido, firma inválida, otro emisor u otra audiencia. */
	public static AuthenticationEntryPoint noAutenticado() {
		BearerTokenAuthenticationEntryPoint estandar = new BearerTokenAuthenticationEntryPoint();
		return (request, response, ex) -> {
			estandar.commence(request, response, ex);
			escribir(response, 401, "No autenticado",
					"Falta un token válido de Microsoft Entra ID, o está vencido o no es para esta API.");
		};
	}

	/** Token válido, pero sin el scope o el rol que pide la ruta. */
	public static AccessDeniedHandler sinPermiso() {
		BearerTokenAccessDeniedHandler estandar = new BearerTokenAccessDeniedHandler();
		return (request, response, ex) -> {
			estandar.handle(request, response, ex);
			escribir(response, 403, "Sin permiso", "Tu cuenta no tiene el rol o el scope necesario para esta acción.");
		};
	}

	private static void escribir(HttpServletResponse response, int status, String titulo, String detalle) throws IOException {
		response.setStatus(status);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setContentType("application/problem+json");
		response.getWriter().write("{\"status\":%d,\"title\":\"%s\",\"detail\":\"%s\"}".formatted(status, titulo, detalle));
	}
}
