# Mocks de pagos y visitas — copia local, en esmeralda

Copia sin tocar de los cuatro artifacts publicados. Se conservan **en esmeralda `#34D399`** a
propósito: son el **registro de lo que se acordó**, no la paleta a implementar.

| Archivo | Artifact |
|---|---|
| `cliente-y-venta.html` | https://claude.ai/code/artifact/c741ddde-ec64-4d1e-a9d0-810346667653 |
| `registrar-abono.html` | https://claude.ai/code/artifact/6b772462-71c5-4be0-8b2a-b4a9dc602653 |
| `registrar-visita.html` | https://claude.ai/code/artifact/836239c8-0c79-4e3d-9a1e-8f000bec9d6a |
| `historial-de-pagos.html` | https://claude.ai/code/artifact/69032026-0c4e-4c4b-a79e-ae2dcf9d3bcd |

## Cómo se usan

- **Composición, escala y jerarquía** → de estos archivos.
- **Color** → NO de estos archivos. Manda `../paleta-mocks-a-mspcolors.md` (Task 2 del plan),
  que traduce cada color del mock a su token de `MspColors`.

La regla: **azul de marca `#2563EB` para acción, verde `statusPaid` solo para estado. El verde
nunca es acción.**
