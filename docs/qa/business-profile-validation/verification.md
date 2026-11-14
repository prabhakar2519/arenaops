# Business Profile validation verification

Executed 2026-11-14. Scope: current Angular form, actual DTO Bean Validation, multipart controller regression, logo decoder and existing profile persistence tests. This review changes tests/documentation only.

| Requirement | Outcome | Executed evidence |
|---|---|---|
| Correct phone format | PASS for existing supported formats | Ten-digit Indian mobile without country prefix, +91, formatting separators and explicit international syntax pass; nine digits, invalid Indian prefix, letters and malformed +91 fail. Blank remains optional. Existing multipart regression verifies fieldErrors.publicPhone and zero save calls. Number ownership/reachability is not verified. |
| Email validation | PARTIAL | Valid standard/plus-addressed emails pass; missing @, malformed domain, spaces and consecutive dots in the local part fail backend @Email. Frontend rejects common malformed input but accepts `owner..name@example.test` in validate(); API rejects it on save. |
| Country-specific postal code | FAIL | Both UI and backend accept `ABCDEF` for India and `ABCDE` for United States. Only postal length30 and country length100 are enforced. Country is a free-text field, not a selected-country catalogue. |
| Logo file bytes | PASS at implemented2MB convention |2,097,152-byte valid PNG accepted;2,097,153 bytes rejected with LOGO_TOO_LARGE. The app uses2*1024*1024 bytes. Browser checks file bytes/type; Core inspects actual image data. |
| Logo documented4MP restriction | FAIL if4MP means4,000,000 pixels |2000x2000 passes, but2001x2000 (4,002,000 pixels) also passes. Current cap is4,194,304 pixels:2048x2048 passes;2049x2048 fails. Each dimension is also capped at4096. |
| Invalid logo content | PASS | Fake PNG content rejected. Existing tests also cover PNG/JPEG/WebP decoding, metadata-free PNG normalization and oversize rejection. |

## Test execution

- `node arena-ui/scripts/audit-business-profile-validation.mjs`:18 tests,16 passed,2 failed (frontend email consistency and India postal validation).
- `mvn -o -f arena-core/pom.xml -Dtest=BusinessProfileValidationAudit,ContactPhoneValidationTest,BusinessProfileTest,BusinessProfileTransactionTest test`:38 tests,35 passed,3 failed (India/US postal constraints and literal4,000,000-pixel cap).
- Audit failures are ordinary assertions and were actually executed; they are not skipped or relabeled as passes. Logs were captured locally under `/tmp/profile-validation-*-audit.log`.
- `BusinessProfileValidationAudit` deliberately has no default Surefire `*Test` suffix. Run it explicitly with `-Dtest=BusinessProfileValidationAudit` to reproduce the known gaps without silently breaking the normal suite. The frontend audit is also a separate explicit command; the normal npm test command is unchanged.
- `git diff --check` passed.

## Source locations

- Angular form validation: `arena-ui/src/app/business-profile/business-profile.component.ts`, `validate()` and `chooseLogo()`.
- Field constraints: `arena-core/src/main/java/com/arena/core/profile/ProfileUpdate.java` and `ContactPhoneValidator.java`.
- Server image validation: `arena-core/src/main/java/com/arena/core/profile/LogoImages.java`, `normalize()`.
- Backend errors are returned through the existing GlobalExceptionHandler; field errors are rendered by BusinessProfileComponent.

## Follow-up needed

Define supported country names/codes and their postal rules (the current field accepts free text), align the client email checks with backend validation, and confirm/enforce the decimal4,000,000-pixel meaning of the documented4MP limit. None of these application rules were altered as part of verification. Existing saved profiles and logos were not rewritten.
