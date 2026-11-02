package ma.daba.commun;

import org.springframework.stereotype.Component;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Lecture et écriture des colonnes jsonb. */
@Component
public class Json {

	private final JsonMapper mapper;

	Json(JsonMapper mapper) {
		this.mapper = mapper;
	}

	public String ecrire(Object valeur) {
		return valeur == null ? null : mapper.writeValueAsString(valeur);
	}

	public <T> T lire(String json, Class<T> type) {
		return json == null ? null : mapper.readValue(json, type);
	}

	public <T> T lire(String json, TypeReference<T> type) {
		return json == null ? null : mapper.readValue(json, type);
	}

	public <T> T convertir(Object valeur, Class<T> type) {
		return mapper.convertValue(valeur, type);
	}

	public JsonMapper mapper() {
		return mapper;
	}

}
