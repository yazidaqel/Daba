import { LANGUES, useI18n } from '@/i18n'

export function SelecteurLangue() {
  const { t, langue, setLangue } = useI18n()
  return (
    <select
      aria-label={t('app.langue')}
      className="h-9 rounded-md border bg-background px-2 text-sm"
      value={langue}
      onChange={(e) => setLangue(e.target.value as typeof langue)}
    >
      {LANGUES.map((l) => (
        <option key={l} value={l}>
          {t(`langue.${l}`)}
        </option>
      ))}
    </select>
  )
}
