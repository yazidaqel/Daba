-- Noms des régions et des villes en darija (§10) : graphie arabe identique à l'arabe, graphie latine identique au français.
UPDATE region SET nom = nom || jsonb_build_object('ary-Arab', nom->>'ar', 'ary-Latn', nom->>'fr');
UPDATE ville SET nom = nom || jsonb_build_object('ary-Arab', nom->>'ar', 'ary-Latn', nom->>'fr');
