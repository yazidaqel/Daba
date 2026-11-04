import { useState } from 'react'
import { Eye, EyeOff } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useI18n } from '@/i18n'

// Champ mot de passe avec bouton afficher / masquer. La valeur ne vit que dans l'état du composant parent.
export function ChampMotDePasse({ id, label, valeur, onChange, autoComplete, aide }: {
  id: string
  label: string
  valeur: string
  onChange: (v: string) => void
  autoComplete: 'current-password' | 'new-password'
  aide?: string
}) {
  const { t } = useI18n()
  const [visible, setVisible] = useState(false)
  return (
    <div className="space-y-1">
      <Label htmlFor={id}>{label}</Label>
      <div className="flex gap-1">
        <Input
          id={id}
          type={visible ? 'text' : 'password'}
          autoComplete={autoComplete}
          autoCapitalize="off"
          spellCheck={false}
          // Pas de limite sur un nouveau mot de passe : maxLength compte en UTF-16 et couperait sans prévenir ;
          // controlerMotDePasse compte en points de code et affiche « trop long ».
          maxLength={autoComplete === 'new-password' ? undefined : 1024}
          value={valeur}
          onChange={(e) => onChange(e.target.value)}
          aria-describedby={aide ? `${id}-aide` : undefined}
          required
        />
        <Button
          type="button"
          variant="outline"
          size="icon"
          aria-pressed={visible}
          aria-controls={id}
          aria-label={t('mdp.afficher')}
          onClick={() => setVisible(!visible)}
        >
          {visible ? <EyeOff aria-hidden /> : <Eye aria-hidden />}
        </Button>
      </div>
      {aide && <p id={`${id}-aide`} className="text-muted-foreground text-sm">{aide}</p>}
    </div>
  )
}
