-- Journal d'audit (§10) en ajout seul : toute modification ou suppression est refusée par la base.
CREATE FUNCTION journal_audit_ajout_seul() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'journal_audit est en ajout seul : % refusé', TG_OP;
END
$$;

CREATE TRIGGER journal_audit_ajout_seul BEFORE UPDATE OR DELETE ON journal_audit
  FOR EACH ROW EXECUTE FUNCTION journal_audit_ajout_seul();
CREATE TRIGGER journal_audit_sans_troncature BEFORE TRUNCATE ON journal_audit
  FOR EACH STATEMENT EXECUTE FUNCTION journal_audit_ajout_seul();
