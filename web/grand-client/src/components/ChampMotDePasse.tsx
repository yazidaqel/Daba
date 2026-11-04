import { useState } from 'react'
import { Eye, EyeOff } from 'lucide-react'
import { useI18n } from '@/i18n'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/** Champ mot de passe avec bouton afficher/masquer (libellé fixe, état porté par aria-pressed). */
export function ChampMotDePasse({ id, label, autoComplete, value, onChange, aide }: {
  id: string
  label: string
  autoComplete: 'current-password' | 'new-password'
  value: string
  onChange: (v: string) => void
  aide?: string
}) {
  const { t } = useI18n()
  const [visible, setVisible] = useState(false)
  return (
    <div className="space-y-2">
      <Label htmlFor={id}>{label}</Label>
      <div className="relative">
        <Input
          id={id}
          type={visible ? 'text' : 'password'}
          autoComplete={autoComplete}
          autoCapitalize="off"
          spellCheck={false}
          className="pe-10"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          aria-describedby={aide ? `${id}-aide` : undefined}
          required
        />
        <Button
          type="button"
          variant="ghost"
          size="icon-sm"
          className="absolute end-0.5 top-0.5"
          aria-label={t('connexion.afficher')}
          aria-pressed={visible}
          aria-controls={id}
          onClick={() => setVisible(!visible)}
        >
          {visible ? <EyeOff aria-hidden /> : <Eye aria-hidden />}
        </Button>
      </div>
      {aide && (
        <p id={`${id}-aide`} className="text-sm text-muted-foreground">
          {aide}
        </p>
      )}
    </div>
  )
}
