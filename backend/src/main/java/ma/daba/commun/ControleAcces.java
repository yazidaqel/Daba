package ma.daba.commun;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Authentifie les appels /v1 (jeton d'accès), applique {@code x-roles} (403 sinon) et les
 * contrôles du compte, puis injecte l'{@link Appelant} dans les contrôleurs.
 */
@Component
class ControleAcces implements WebMvcConfigurer, HandlerInterceptor, HandlerMethodArgumentResolver {

	private static final String ATTRIBUT = Appelant.class.getName();

	private final Jetons jetons;
	private final Acces.VerificationCompte verificationCompte;

	ControleAcces(Jetons jetons, Acces.VerificationCompte verificationCompte) {
		this.jetons = jetons;
		this.verificationCompte = verificationCompte;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(this).addPathPatterns("/v1/**");
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(this);
	}

	@Override
	public boolean preHandle(HttpServletRequest requete, HttpServletResponse reponse, Object handler) {
		if (!(handler instanceof HandlerMethod methode) || methode.hasMethodAnnotation(Acces.Public.class)) {
			return true;
		}
		var appelant = authentifier(requete.getHeader("Authorization"));
		var roles = methode.getMethodAnnotation(Acces.Roles.class);
		if (roles == null || Arrays.stream(roles.value()).noneMatch(appelant::a)) {
			throw new Probleme(HttpStatus.FORBIDDEN, "role-non-autorise", null, "Rôle non autorisé pour cette opération");
		}
		verificationCompte.verifier(appelant, !methode.hasMethodAnnotation(Acces.SansConsentement.class));
		requete.setAttribute(ATTRIBUT, appelant);
		return true;
	}

	private Appelant authentifier(String entete) {
		if (entete == null || !entete.startsWith("Bearer ")) {
			throw Probleme.nonAuthentifie();
		}
		try {
			var c = jetons.verifier(entete.substring(7), "acces");
			var roles = ((Collection<?>) c.get("roles")).stream()
				.map(r -> Appelant.Role.valueOf(r.toString()))
				.collect(Collectors.toUnmodifiableSet());
			return new Appelant(uuid(c.get("sub")), uuid(c.get("org")), roles, uuid(c.get("app")), uuid(c.get("hanout")));
		}
		catch (RuntimeException e) {
			throw Probleme.nonAuthentifie();
		}
	}

	private static UUID uuid(Object valeur) {
		return valeur == null ? null : UUID.fromString(valeur.toString());
	}

	@Override
	public boolean supportsParameter(MethodParameter parametre) {
		return parametre.getParameterType() == Appelant.class;
	}

	@Override
	public Object resolveArgument(MethodParameter parametre, ModelAndViewContainer mav, NativeWebRequest requete,
			WebDataBinderFactory binderFactory) {
		return requete.getAttribute(ATTRIBUT, NativeWebRequest.SCOPE_REQUEST);
	}

}
