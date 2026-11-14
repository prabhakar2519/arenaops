# Business Profile contact phone validation

Verified 2026-11-14.

Edit Business Profile now validates the optional Contact phone field before sending a save. Indian mobiles require ten digits starting6–9; +91 is optional. Formatting spaces, parentheses, hyphens and dots are accepted. Other explicitly international numbers retain the existing international syntax support. Blank remains allowed. This checks number format, not ownership or reachability.

The backend ProfileUpdate DTO uses a field-level ContactPhone constraint with the same rules. Invalid multipart profile updates return VALIDATION_FAILED with fieldErrors.publicPhone before the save service runs. Existing stored formatting and other profile fields are unchanged; no migration or retrospective rewrite occurs.

Changed application files: business-profile.component.ts/.html; ProfileUpdate.java; ContactPhone.java; ContactPhoneValidator.java. Added tests in test-business-profile.mjs, ContactPhoneValidationTest and business-profile-phone.spec.ts; updated an old invalid test fixture to a valid formatted phone.

Executed:

- 188 frontend tests passed.
- Profile/transaction/phone/OpenAPI suite:25 passed. The added multipart API regression was then executed in ContactPhoneValidationTest (3 passed).
- Playwright:2 passed (desktop/light and tablet/dark). Invalid nine-digit, alphabetic and incomplete +91 input causes no save request; a local ten-digit number saves successfully.
- Angular production build, E2E TypeScript check, OpenAPI validation and diff checks passed.

Browser APIs use isolated synthetic fixtures; the multipart rejection test invokes the actual controller/Bean Validation/error handler with a mocked save service to verify zero writes on invalid input.
