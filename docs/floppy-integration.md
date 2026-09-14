# Floppy integration

CineTrack connects to a self-hosted Floppy instance from Settings. Use the
server URL including any reverse-proxy prefix (for example
`https://media.example/floppy`) and an API token generated in Floppy under
Settings → Integrations.

CineTrack uses Floppy's reviewed OpenAPI 1.0.0 contract: `/api/v1/info/` for
public discovery, `/api/v1/user/preferences/` for authenticated validation,
the paginated `/api/v1/media/{media_type}/` and `/api/v1/history/` reads, and
the documented media/episode tracking routes. Authentication is the
`X-API-Key` header. The token is stored only in the Android Keystore-backed
credential store; the server URL and non-secret capability metadata are kept
in DataStore.

Floppy starts as a SECONDARY provider. CineTrack sends a durable canonical
bootstrap targeted only to Floppy and resumes it after interruption. A Floppy
server can be promoted to MAIN only after the bootstrap is READY and the
server exposes complete library/history reads; a server with write-only or
incomplete pull support remains SECONDARY.

Changing the server URL or authenticated account invalidates Floppy's
baseline, cursor/check state, and bootstrap. Outstanding deliveries for the
old instance are cancelled and a fresh canonical bootstrap is created for the
new instance. Disconnecting Floppy removes its token, role, and provider
deliveries without changing CineTrack's local library or history.

For connection failures, check that the URL is reachable, uses an explicit
`http://` or `https://` scheme, and that the token is valid. TLS verification
is never bypassed; plain HTTP is intended only for a deliberately configured
trusted local network.
