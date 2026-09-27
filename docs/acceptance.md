# Acceptance checklist

Before distributing example JARs, use an isolated Paper26.2/Java25 server with the exact commercial renderer release. Do not test on a customer's server.

- Postcard produces a map; full inventory/disconnect cannot drop or duplicate delivery.
- Ordinary dispensers are unchanged. Tagged camera responds once per rising edge, ignores sustained power and busy bursts, and refuses stale replaced/unloaded destinations.
- Easel placement respects cancelled placement, is creator-only, captures one fixed reference, and preserves progress/maps across restart.
- Brush hits reach correct map pixels from all four horizontal orientations; wrong dye does nothing; overlapping CMY layers converge without exceeding target pigments.
- Creative/survival punches cannot destroy or rotate the canvas. Collection requires inventory space and cannot collect twice.
- Provider removal, plugin disable and timeouts leave no usable late callback.
- Exact example JARs contain no API implementation classes, native binaries, license keys or private renderer source.

Automated Paper and visual-playtest results will be recorded here with the tested artifact hashes. Windows is not covered.
