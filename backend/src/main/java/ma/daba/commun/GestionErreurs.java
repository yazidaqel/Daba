package ma.daba.commun;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class GestionErreurs extends ResponseEntityExceptionHandler {

	static final String BASE_TYPE = "https://daba.ma/problemes/";

	@ExceptionHandler(Probleme.class)
	ResponseEntity<ProblemDetail> probleme(Probleme p) {
		var detail = ProblemDetail.forStatusAndDetail(p.statut(), p.getMessage());
		detail.setType(URI.create(BASE_TYPE + p.code()));
		if (p.regle() != null) {
			detail.setProperty("regle", p.regle());
		}
		var reponse = ResponseEntity.status(p.statut());
		if (p.reessayerDans() != null) {
			reponse.header(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, p.reessayerDans().toSeconds())));
		}
		return reponse.body(detail);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {
		var erreurs = ex.getBindingResult().getFieldErrors().stream()
			.map(e -> Map.of("champ", e.getField(), "message", String.valueOf(e.getDefaultMessage())))
			.toList();
		return invalide(erreurs, headers);
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		var erreurs = ex.getParameterValidationResults().stream()
			.flatMap(r -> r.getResolvableErrors().stream()
				.map(e -> Map.of("champ", String.valueOf(r.getMethodParameter().getParameterName()),
						"message", String.valueOf(e.getDefaultMessage()))))
			.toList();
		return invalide(erreurs, headers);
	}

	private ResponseEntity<Object> invalide(List<Map<String, String>> erreurs, HttpHeaders headers) {
		var detail = ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatus.BAD_REQUEST, "Requête invalide");
		detail.setType(URI.create(BASE_TYPE + "requete-invalide"));
		detail.setProperty("erreurs", erreurs);
		return ResponseEntity.badRequest().headers(headers).body(detail);
	}

}
