# QA Execution Report 001 — Club Setup v1.0

Execution date: 24 October 2026 (Asia/Kolkata). Overall automated baseline: **PASS**.

All five requested stages ran in order. No application/test source was changed, no service/container was started or stopped, and no database, Keycloak, SMTP or deployment operation was performed. Only existing tests were executed. Maven compilation and mock application contexts are test execution, not running service deployments.

## Results

| Stage | Executed tests/checks | Passed | Failed/errors | Skipped | Wall duration (s) | Outcome |
|---|---:|---:|---:|---:|---:|---|
| 01-openapi | 1 check | 1 | 0 | 0 | 5.724 | PASS |
| 02-ui | 116 | 116 | 0 | 0 | 17.421 | PASS |
| 03-core | 68 | 68 | 0 | 0 | 21.866 | PASS |
| 04-login | 9 | 9 | 0 | 0 | 18.975 | PASS |
| 05-contract | 6 | 6 | 0 | 0 | 61.547 | PASS |

**199 automated tests passed, 0 failed/errors, 0 skipped**, plus one successful offline validation invocation covering 87 API operations (66 Core, 21 Login). The final contract stage repeats offline validation; that repeat is not counted as an additional test. Total command wall duration: 125.533 seconds. None of the five stages was BLOCKED.

No test failure occurred: exact failure, probable cause and test-versus-production defect classification are **not applicable** for all rows below. Class/suite times exclude some JVM/Node startup and build overhead; stage wall times above include it.

### UI suites (Node TAP output)

| Existing script | Executed | Passed | Failed | Skipped | TAP duration (s) |
|---|---:|---:|---:|---:|---:|
| test-errors.mjs | 5 | 5 | 0 | 0 | 0.939576 |
| test-billing.mjs | 8 | 8 | 0 | 0 | 0.088958 |
| test-auth.mjs | 10 | 10 | 0 | 0 | 0.062216 |
| test-training.mjs | 31 | 31 | 0 | 0 | 0.230200 |
| test-business-profile.mjs | 13 | 13 | 0 | 0 | 0.092872 |
| test-sports-activities.mjs | 28 | 28 | 0 | 0 | 0.092208 |
| test-facilities.mjs | 21 | 21 | 0 | 0 | 0.182191 |

These are the package’s existing Angular-source unit/component/service checks using `node:test`, transpilation and injected fakes. They are not browser-rendered Angular TestBed or E2E tests. Auth TAP counts include dynamically generated cases.

### Backend and mock-context classes

| Stage | Fully qualified test class | Executed | Passed | Failed/errors | Skipped | JUnit duration (s) |
|---|---|---:|---:|---:|---:|---:|
| 03-core | `com.arena.core.facility.FacilityServiceTest` | 15 | 15 | 0 | 0 | 12.477 |
| 03-core | `com.arena.core.profile.BusinessProfileTest` | 17 | 17 | 0 | 0 | 1.854 |
| 03-core | `com.arena.core.service.AdminCustomerServiceTest` | 13 | 13 | 0 | 0 | 0.263 |
| 03-core | `com.arena.core.service.CustomerAccessServiceTest` | 1 | 1 | 0 | 0 | 0.076 |
| 03-core | `com.arena.core.service.OwnerRegistrationTest` | 1 | 1 | 0 | 0 | 0.064 |
| 03-core | `com.arena.core.training.ManagerAuthorizationTest` | 8 | 8 | 0 | 0 | 0.038 |
| 03-core | `com.arena.core.training.StaffLoginServiceTest` | 13 | 13 | 0 | 0 | 0.215 |
| 04-login | `com.arena.login.controller.BusinessProfileProxyTest` | 3 | 3 | 0 | 0 | 0.064 |
| 04-login | `com.arena.login.controller.FacilityProxyTest` | 2 | 2 | 0 | 0 | 12.436 |
| 04-login | `com.arena.login.controller.RegistrationProxyTest` | 1 | 1 | 0 | 0 | 0.031 |
| 04-login | `com.arena.login.service.TokenServiceErrorTest` | 1 | 1 | 0 | 0 | 0.521 |
| 04-login | `com.arena.login.service.TokenServicePkceTest` | 2 | 2 | 0 | 0 | 0.04 |
| 05-contract | `com.arena.core.documentation.DocumentationSecurityTest` | 1 | 1 | 0 | 0 | 0.726 |
| 05-contract | `com.arena.core.documentation.OpenApiContractTest` | 2 | 2 | 0 | 0 | 23.046 |
| 05-contract | `com.arena.login.documentation.DocumentationSecurityTest` | 1 | 1 | 0 | 0 | 0.451 |
| 05-contract | `com.arena.login.documentation.OpenApiContractTest` | 2 | 2 | 0 | 0 | 18.296 |

## Isolation review completed before execution

- Offline validator reads the checked-in YAML, vendored OpenAPI schema, source fingerprints and local generated inventories. It does not contact services and was run without `--update`.
- UI scripts use in-memory RxJS results, injected HttpClient/API/Auth fakes, fake browser storage/navigation, and source inspection. External-looking URLs in assertions are data, not network requests. No browser or development server was launched.
- Selected Core tests directly construct services with Mockito repositories. Owner provisioning mocks KeycloakService; customer creation mocks InvitationEmailService; staff tests mock KeycloakService and AccountSetupNotification. Facility ledger/audit/persistence are mocked. Logo tests decode local synthetic/file image fixtures without uploading anywhere. None uses the production Spring Boot context or datasource.
- Selected Login tests use mocked RestTemplate/TokenService, MockHttpSession and MockHttpServletRequest. TokenService HTTP calls are intercepted by MockRestServiceServer, including token/user-info expectations. No live Keycloak/BFF/Core call is needed.
- OpenApiContractTest explicitly uses a documentation-only MOCK web context and `openapi-contract` profile. It excludes datasource/JPA/Liquibase, OAuth client/resource-server and default security auto-configuration. Core business collaborators and Login outbound RestTemplate/TokenService are Mockito beans. DocumentationSecurityTest constructs only a mock web/security context. No HTTP listener is opened.
- Maven POMs were checked for test lifecycle hooks: no start/deploy/container execution is attached to these commands. Maven ran offline (`-o`) against the existing `/tmp/arena-openapi-m2` cache, with no dependency downloads.
- Child environments removed ARENA_, SPRING_, BREVO_, KC_ and KEYCLOAK_ overrides and MAVEN_ARGS/JAVA_TOOL_OPTIONS/JDK_JAVA_OPTIONS/NODE_OPTIONS without printing their values. Explicit class selections exclude all PostgreSQL/Keycloak integration tests. Isolation relies on inspected test construction/mocks; no packet capture or syscall-level network audit was performed.

## Exact commands executed, in order

The Maven options add offline execution and the existing writable cache to the readiness-report commands; selected test classes are unchanged.

```sh
python3 scripts/validate-openapi.py
npm --prefix arena-ui test
mvn -o -Dmaven.repo.local=/tmp/arena-openapi-m2 -f arena-core/pom.xml -Dtest=OwnerRegistrationTest,AdminCustomerServiceTest,CustomerAccessServiceTest,ManagerAuthorizationTest,StaffLoginServiceTest,FacilityServiceTest,BusinessProfileTest test
mvn -o -Dmaven.repo.local=/tmp/arena-openapi-m2 -f arena-login/pom.xml -Dtest=TokenServicePkceTest,TokenServiceErrorTest,RegistrationProxyTest,FacilityProxyTest,BusinessProfileProxyTest test
bash scripts/export-openapi.sh --check -o -Dmaven.repo.local=/tmp/arena-openapi-m2
```

## Confluence scenario mapping

Source: user-provided screenshot of “ArenaOps Admin & Owner Onboarding Test Scenarios Overview v1.0”, Scenario Group A. The initial screenshot shows ONB-001–ONB-006; subsequent screenshots supplied after execution show ONB-007–ONB-030. The full private Confluence page/scenario catalogue was not read or changed; no unseen scenario IDs are invented. A passing unit check does not change a manual/API/E2E scenario to PASS.

| Visible scenario | Existing baseline evidence | Scenario-level outcome |
|---|---|---|
| ONB-001 Admin creates academy customer | AdminCustomerServiceTest.createsInvitationWithoutStartingTrial; UI training/sports checks provide separate setup coverage | PARTIAL unit evidence; real admin API + Owner onboarding smoke NOT RUN |
| ONB-002 Required-field validation | UI errors suite and BusinessProfileTest validation checks pass, but are not complete admin customer request validation | NOT RUN as a full admin scenario |
| ONB-003 Invalid email/field formats | UI email-validator checks and profile validation pass; full admin request/API format matrix not executed | PARTIAL supporting evidence |
| ONB-004 Duplicate academy/customer | AdminCustomerServiceTest.duplicateEmailIsRejectedBeforeCreatingCustomerOrInvitation | PARTIAL: duplicate-email unit behavior passed; database uniqueness/concurrency NOT RUN |
| ONB-005 Unauthorized customer creation | RegistrationProxyTest blocks anonymous forwarding to admin creation; documentation security check separately denies business paths | PARTIAL: anonymous BFF check passed; authenticated non-admin Core authorization matrix NOT RUN |
| ONB-006 Missing/invalid JWT on creation | RegistrationProxyTest has anonymous BFF rejection only; selected suite does not exercise a real JWT security filter | PARTIAL for missing BFF session; direct Core missing/invalid JWT scenario NOT RUN |

### Additional mapping from follow-up screenshots (ONB-007–ONB-030)

This is a documentation-only mapping update using the same execution evidence. No additional tests were run. All full Confluence scenarios below remain **NOT RUN**; PARTIAL identifies passing supporting unit assertions only. Screenshots provide scenario titles, priorities and methods, but not their complete steps/acceptance criteria.

| Scenario | Existing executed evidence | Coverage and remaining gap |
|---|---|---|
| ONB-007 Valid invitation email generated/delivered | AdminCustomerServiceTest.createsInvitationWithoutStartingTrial | PARTIAL: mocked SENT outcome; real message generation, SMTP delivery and inbox NOT RUN |
| ONB-008 Correct customer/activation information in email | No executed email-template/delivery test | NOT RUN: activation association checks do not establish rendered email correctness |
| ONB-009 Email delivery failure handled | AdminCustomerServiceTest.emailDisabledOrProviderFailurePreservesCreatedCustomerAndPreparedInvitation; resendReportsActualDeliveryOutcomeAndRetainsReplacementCodeOnFailure | PARTIAL: mocked disabled/failing provider preserves customer/invitation; real SMTP fault injection NOT RUN |
| ONB-010 Authorized Admin resends invitation | AdminCustomerServiceTest.resendReportsActualDeliveryOutcomeAndRetainsReplacementCodeOnFailure | PARTIAL: resend service outcomes tested; authorization filter and real API delivery NOT RUN |
| ONB-011 Old/new invitation behavior after resend | Same resend test | PARTIAL: replacement/outcome and consumed-invitation rejection assertions; complete old/new link replay matrix NOT RUN |
| ONB-012 Rapid/repeated invitation requests | No matching executed concurrency/reliability test | NOT RUN |
| ONB-013 Owner activates valid invitation | AdminCustomerServiceTest.activationRequiresInvitedEmailAndConsumesCodeOnce; OwnerRegistrationTest.licenseRegistrationCreatesActiveOwnerWithoutSetupPrerequisitesAndBindsSubject | PARTIAL: service consumption, Owner creation and subject binding with mocks; complete UI/Keycloak activation NOT RUN |
| ONB-014 Used invitation cannot activate again | AdminCustomerServiceTest.activationRequiresInvitedEmailAndConsumesCodeOnce | PARTIAL: sequential replay rejected in-memory; API/persistence replay NOT RUN |
| ONB-015 Expired activation invitation rejected | AdminCustomerServiceTest.invitationValidationReturnsDistinctCodesForInvalidExpiredConsumedRevokedAndWrongEmail | PARTIAL: EXPIRED-status validation rejection; elapsed-time expiry through activation API NOT RUN |
| ONB-016 Tampered/invalid activation token rejected | Same invitation-validation test | PARTIAL: unknown-code lookup rejection; real token hashing/tampering/security boundary NOT RUN because ActivationCodeService is mocked |
| ONB-017 First-time password/email setup | OwnerRegistrationTest with mocked KeycloakService | NOT RUN for required acceptance: no real password setup or email verification occurred; staff required-action tests cannot substitute for Owner behavior |
| ONB-018 Simultaneous activation consumes token once | No real database/concurrency test executed | NOT RUN: sequential replay assertions cannot prove locking/atomic consumption |
| ONB-019 Activated Owner logs in | TokenServicePkceTest plus UI auth tests | PARTIAL: mocked code exchange and client PKCE behavior; newly activated Owner real login/session NOT RUN |
| ONB-020 Correct academy-scoped Owner permissions | OwnerRegistrationTest binds customer/subject; BusinessProfileTest and FacilityServiceTest exercise scoped operations with mocked authorization | PARTIAL: no complete real Owner JWT/API authorization matrix |
| ONB-021 Owner cannot access another academy | BusinessProfileTest tenant-logo scope and FacilityServiceTest tenant boundary checks | PARTIAL: isolated service boundaries; live cross-academy Owner requests/database isolation NOT RUN |
| ONB-022 Trial starts on activation, not invitation creation | AdminCustomerServiceTest.createsInvitationWithoutStartingTrial; openingInvitationDoesNotConsumeCodeOrStartTrial; activationRequiresInvitedEmailAndConsumesCodeOnce | PARTIAL: service state assertions passed; transaction/persistence integration NOT RUN |
| ONB-023 Trial expiry restricts access | CustomerAccessServiceTest.trialExpiryCancellationAndSuspensionProduceDistinct403CodesWithoutChangingEntitlements | PARTIAL: mocked entitlement rejection and state changes; full BFF/API enforcement NOT RUN |
| ONB-024 Subscription reactivation restores permitted access | AdminCustomerServiceTest cancelledCustomersCanReactivateAndPayWithoutRestartingTrial, cancelledCustomerCanReceiveGraceAfterReactivation, suspendedCustomerReactivationRequiresUnexpiredPaidPeriod | PARTIAL: service state/eligibility assertions; real subscription API/login access restoration NOT RUN |
| ONB-025 Repeated customer creation submission | AdminCustomerServiceTest.duplicateEmailIsRejectedBeforeCreatingCustomerOrInvitation; UI email duplicate checks | PARTIAL: pre-existing duplicate rejection; double-submit race/transaction uniqueness NOT RUN |
| ONB-026 Timeout with uncertain customer-creation result | No matching executed customer-creation timeout test | NOT RUN: facility idempotency tests do not establish customer-creation retry safety |
| ONB-027 Partial Keycloak/database operation failure | StaffLoginServiceTest fault simulations | PARTIAL only for staff failure handling; Owner activation cross-system compensation/rollback NOT RUN |
| ONB-028 Invalid requests return structured errors | UI errors suite; BusinessProfileTest multipart validation; Login TokenServiceErrorTest and FacilityProxyTest | PARTIAL: selected validation/error mapping behavior; admin/activation real API contract matrix NOT RUN |
| ONB-029 Activation audit and security-sensitive logging | Selected services mock AuditService; profile audit metadata and error sanitization assertions exist | NOT RUN for full activation audit persistence and sensitive-log verification; no comprehensive log-leak test |
| ONB-030 Complete fresh Owner onboarding regression | Individual mocked unit suites only | NOT RUN: no fresh end-to-end customer/email/activation/login flow executed |

The baseline PASS remains limited to the 199 executed automated tests. It does not establish PASS for any full manual/integration/E2E Confluence scenario. No Confluence page or status was modified.


## Warnings, failures and classification

- **No failed tests, compilation failures, skipped selected tests or isolation blockers.**
- Core customer-service warnings EMAIL_DISABLED and EMAIL_DELIVERY_FAILED are deliberate mocked negative cases, not real mail failures or observed production defects.
- Mock OpenAPI generation still emits the known springdoc 2.8.13 `additionalProperties must be either a Boolean or a Schema instance` / cloneViaJson warning. Probable cause: generator handling of map-valued OpenAPI 3.1 schemas. Classify as an existing documentation-tooling compatibility issue, not a failed test or established business-code defect. JSON/YAML/Swagger mock assets and snapshot checks passed. No fix was attempted.

## Evidence and limits

- Raw logs and run metadata: `/tmp/arena-qa-001/{01-openapi,02-ui,03-core,04-login,05-contract}.log` and `results.json`. Copied per-run Surefire XML is in its stage subdirectory; the report filters by module/class to exclude stale cross-module reports. These local artifacts are temporary and are not published.
- Counts/times above come from fresh TAP output and Surefire XML, not previous runs. Framework tests may contain multiple assertions or looped cases counted as one JUnit test.
- No real PostgreSQL locking/transactions, Keycloak action-link expiry/replay, email delivery, browser rendering, live entitlement checks or cross-tenant database calls were executed. They remain NOT RUN/outside this isolated baseline, not silently counted as skipped passing tests.
- Existing UI package tests include some billing/training assertions because the approved `npm test` command runs the complete existing package; no new suites or operational features were added.
- Only this report is a new source-workspace deliverable. Build outputs under service `target/` and temporary logs were generated as part of the authorized tests. The existing `/docs/*` ignore rule ignores `docs/qa/`; this report remains available locally. No ignore rules were changed and no commit/push was performed.

**Conclusion: PASS for QA Execution 001’s existing isolated automated baseline. Live onboarding and full Confluence scenario acceptance remain NOT RUN.**
