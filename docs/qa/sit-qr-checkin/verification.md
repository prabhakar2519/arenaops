# SIT public QR check-in signing repair

## Root cause

The QR page loaded, but BFF returned RESOURCE_NOT_FOUND before public QR inspection reached Core. Read-only checks found `PUBLIC_CLUB_HOST_KEY` absent in both running SIT application services and absent from the SIT GitHub Environment. Host signing therefore failed closed. This was independent of the earlier Keycloak subject-scope issue and independent of Public Club Website publication.

The existing deployment configuration treated the signing key as optional even though QR check-in is enabled for the MVP. A successful container health check did not validate the public QR path.

## Live SIT repair

Generated a cryptographically random environment-specific signing key, saved it as the SIT GitHub Environment secret `PUBLIC_CLUB_HOST_KEY`, and applied the same value to SIT Core and BFF. The value was never printed, committed, sent to the browser or copied into documentation. Protected temporary local and remote files were removed after use.

Recreated only `sit-arena-core` and `sit-arena-login` through the existing Compose configuration. Preflight asserted the SIT project, current release images, environment preservation and unchanged container identities before applying. Both services became healthy. The script included rollback to their original configuration on failed health checks. The UI container, infrastructure containers and PROD were unchanged. No application image or database migration was deployed.

A before/after comparison confirmed permanent QR identifiers and revocation states were unchanged. Existing printed/displayed QR codes remain usable. BFF recreation can sign staff out; signing in again restores their normal authenticated session. Anonymous customers can simply reload the existing QR page.

## Deployment safeguard

`deploy/config.py` now requires a nonblank `PUBLIC_CLUB_HOST_KEY` with at least 32 characters before deployment starts. The runtime writer rejects missing configuration before creating its credentials file. Tests verify the same key reaches Core/BFF, stays out of Compose public exports/UI, and does not enable website publishing or public preview. README documents independent secrets for SIT and PROD. No production configuration was changed; future production deployment requires its own secret.

Modified files: `deploy/config.py`, `deploy/tests/test_deployment.py`, `README.md`, and these QA artifacts.

## Executed verification

- `./deploy/validate.sh`: 20 tests passed, including required/blank/short key checks, Core/BFF key sharing, secret isolation and runtime-writer fail-fast behavior.
- `mvn -q -f arena-core/pom.xml -Dtest=PublicClubsIntegrationTest test`: 47 passed; no failures/errors/skips.
- `mvn -q -f arena-login/pom.xml -Dtest=PublicCheckinProxyTest test`: 14 passed; no failures/errors/skips. Existing shared-host, signing, proxy and public-route restrictions remain enforced.
- Real anonymous SIT public inspection: three existing valid facility QR codes returned HTTP 200 with facility, expiry and form token. Unknown QR returned HTTP 410 and stayed rejected.
- Customer, check-in and session counts unchanged during the inspection smoke test. Only normal short-lived form challenges were created; no customer submissions, check-in approvals, sessions, reservations or payments were performed.
- Isolated real-site browser contexts at desktop 1440px and mobile 390px: form and inputs visible without login, no error, no horizontal overflow, blank fields and nine-digit mobiles prevent submission, valid name/mobile plus consent enables the action. No submit request was sent. Screenshots are `desktop.png` and `mobile.png`; no tokens or customer details are shown.

Application code, API contracts, pricing, billing, permissions and QR identities were not modified. No Angular rebuild was needed for this deployment-only repair.
