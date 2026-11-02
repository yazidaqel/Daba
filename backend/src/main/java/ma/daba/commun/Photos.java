package ma.daba.commun;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import ma.daba.commun.Acces.Roles;
import ma.daba.commun.Appelant.Role;

/** Photos envoyées par les utilisateurs (bon de livraison d'un dépôt, rupture), servies aux utilisateurs connectés. */
@RestController
public class Photos {

	private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp");

	private final JdbcClient jdbc;
	private final String baseUrl;

	Photos(JdbcClient jdbc, @Value("${daba.api.base-url}") String baseUrl) {
		this.jdbc = jdbc;
		this.baseUrl = baseUrl;
	}

	public UUID enregistrer(MultipartFile fichier) {
		if (fichier == null || fichier.isEmpty() || !TYPES.contains(fichier.getContentType())) {
			throw Probleme.requete("Photo JPEG, PNG ou WebP requise");
		}
		var id = UUID.randomUUID();
		try {
			jdbc.sql("INSERT INTO photo (id, type_contenu, contenu) VALUES (:id, :type, :contenu)")
				.param("id", id).param("type", fichier.getContentType()).param("contenu", fichier.getBytes()).update();
		}
		catch (IOException e) {
			throw Probleme.requete("Photo illisible");
		}
		return id;
	}

	public String url(UUID id) {
		return id == null ? null : baseUrl + "/v1/photos/" + id;
	}

	/** Identifiant non devinable ; ajouté au contrat en v0.3.0. */
	@GetMapping("/v1/photos/{photoId}")
	@Roles({ Role.AMBULANT, Role.HANOUT, Role.GROSSISTE, Role.GRAND_CLIENT, Role.ADMIN })
	ResponseEntity<byte[]> lire(@PathVariable UUID photoId) {
		return jdbc.sql("SELECT type_contenu, contenu FROM photo WHERE id = :id").param("id", photoId)
			.query((rs, i) -> ResponseEntity.ok().contentType(MediaType.parseMediaType(rs.getString(1))).body(rs.getBytes(2)))
			.optional().orElseThrow(Probleme::introuvable);
	}

}
