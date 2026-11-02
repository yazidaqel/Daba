package ma.daba.commun;

import java.util.List;

/** Page de résultats (PageMeta + contenu du contrat). */
public record Page<T>(int page, int taille, long total, List<T> contenu) {

	public int offset() {
		return page * taille;
	}

	public static <T> Page<T> de(int page, int taille, long total, List<T> contenu) {
		return new Page<>(page, taille, total, contenu);
	}

}
