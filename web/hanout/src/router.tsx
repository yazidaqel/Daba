import { useSyncExternalStore, type ComponentProps } from 'react'

// ponytail: routeur maison (une dizaine d'écrans), react-router si l'app grossit.
const abonner = (f: () => void) => {
  addEventListener('popstate', f)
  return () => removeEventListener('popstate', f)
}

export const useUrl = () => useSyncExternalStore(abonner, () => location.pathname + location.search)

export function naviguer(vers: string, remplacer = false) {
  history[remplacer ? 'replaceState' : 'pushState'](null, '', vers)
  dispatchEvent(new PopStateEvent('popstate'))
  scrollTo(0, 0)
}

/** correspond('/documents/:id', '/documents/42') → { id: '42' } */
export function correspond(motif: string, chemin: string): Record<string, string> | null {
  const m = motif.split('/')
  const c = chemin.split('/')
  if (m.length !== c.length) return null
  const params: Record<string, string> = {}
  for (let i = 0; i < m.length; i++) {
    if (m[i].startsWith(':') && c[i]) params[m[i].slice(1)] = decodeURIComponent(c[i])
    else if (m[i] !== c[i]) return null
  }
  return params
}

export function Lien({ vers, children, ...props }: ComponentProps<'a'> & { vers: string }) {
  return (
    <a
      href={vers}
      {...props}
      onClick={(e) => {
        if (e.button || e.metaKey || e.ctrlKey || e.shiftKey) return
        e.preventDefault()
        naviguer(vers)
      }}
    >
      {children}
    </a>
  )
}
