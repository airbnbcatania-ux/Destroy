# DESTROY

Tower defense incrementale con un albero delle evoluzioni: difendi la torre principale, potenziala con moduli ed elementi, sblocca nuove abilità e guadagna badge sul profilo giocatore.

## Gioca

- **Online:** apri il link di GitHub Pages del repository (es. `https://TUONOME.github.io/destroy/`).
- **In locale:** scarica `index.html` e aprilo con un browser qualsiasi. Non serve installare niente.

Su telefono puoi aggiungerlo alla schermata Home ("Aggiungi a Home") e si apre a schermo intero come un'app.

## Caratteristiche

- Home colorata con il titolo animato
- Albero delle evoluzioni che sblocca nuovi contenuti
- Moduli ed elementi aggiuntivi per la torre principale
- Profilo giocatore con badge
- Effetti 3D e audio in stile arcade
- Salvataggio automatico dei progressi nel browser

## Struttura

| File | Cosa contiene |
|------|---------------|
| `index.html` | Tutto il gioco (grafica, logica, audio) in un unico file |
| `.nojekyll` | Dice a GitHub Pages di servire i file così come sono |

## Note tecniche

- I progressi sono salvati nel `localStorage` del browser: cambiando browser o dispositivo si riparte da zero.
- L'unica risorsa esterna è il font *Russo One* da Google Fonts; senza connessione il gioco funziona lo stesso con un font di sistema.
