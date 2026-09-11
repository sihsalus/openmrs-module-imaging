# OpenMRS Imaging for SIH Salus

This module links Orthanc studies to OpenMRS patients and exposes the imaging
worklist API. The maintained frontend is the
[SIH Salus imaging workspace](https://github.com/sihsalus/sihsalus-frontend/tree/main/packages/apps/esm-patient-imaging-app).
The [deployment runbook](https://github.com/sihsalus/sihsalus/blob/main/imaging/README.md)
describes Orthanc, OHIF, authentication and acceptance in DEV/QLTY.

## Version 1.2.9: source changes awaiting validation

The changes in this checkout are not a published or deployed release. The SIH
Salus distribution still pins 1.2.8 until 1.2.9 has passed CI, migration review and
synthetic integration acceptance. Changing this POM alone does not update a
running backend.

### Uploads and patient associations

- Upload one DICOM file at a time. ZIP archives, including ZIP content disguised
  with another extension, are rejected before submitting bytes to Orthanc.
- `imaging.maxUploadImageDataSize` must be a positive integer in bytes. Its default
  is 200,000,000 bytes. Requests over the configured limit return HTTP 413.
- OpenMRS parses multipart before authentication with a shared resolver. Imaging
  retains its existing configuration of that resolver, now from `contextRefreshed()`
  so another module's restart does not silently reset it. The complete request
  allows an additional 64 KiB for the normal form's multipart metadata; the
  controller still enforces the exact file limit. Increasing the global property
  requires a context refresh/restart. This does not create an imaging-only parser
  or change other endpoints' gateway caps. Invalid/overflowing limits leave the
  framework parser unchanged and are rejected by the imaging controller.
- A successful Orthanc upload must identify a persisted parent study. Malformed,
  incomplete or unexpected responses do not count as successful uploads.
- Repeating an association with the same patient preserves its review state.
  Assigning or unlinking a study owned by a different patient returns HTTP 409.
  An authorized user must explicitly unlink it from the original chart first.
- Owner checks reload the study under a database row lock. Orthanc metadata
  synchronization preserves the current OpenMRS owner.
- Orthanc and OpenMRS do not share a transaction. If storage succeeds and the
  response or association subsequently fails, inspect the study before retrying.
  The module neither retries automatically nor deletes uncertain uploads.

### Worklists

- `PatientID` uses the OpenMRS patient UUID. The callback must return this exact
  UUID, the scheduled step ID and the matching accession number. No fuzzy patient
  matching or numeric database ID fallback is applied.
- Dates and times are stored/exported as DICOM DA (`yyyyMMdd`) and TM (`HHmmss`).
  Valid legacy ISO inputs are normalized; impossible dates and invalid DICOM
  field lengths or characters are rejected.
- New requests receive a stable `2.25` Study Instance UID before acquisition.
- A callback validates every series before clinical changes, completes all
  eligible steps from one request and preserves rejected steps. Different
  requests cannot be combined in one notification.
- Before automatic association, it also checks the stored study's `PatientID`
  and Study Instance UID directly in Orthanc. Matching notification fields alone
  cannot establish ownership of the stored images.
- Request and step versions reject stale writes rather than overwrite a
  concurrent rejection or completion. Failed or conflicting transactions require
  inspection and a controlled retry by the integration caller.

### Database migration

Liquibase changeset 418 adds uniqueness constraints to each Orthanc
configuration's study identifiers. It deliberately halts if duplicate Study
Instance UIDs or Orthanc study IDs already exist. Reconcile duplicates and
patient ownership with the clinical team before proceeding; do not delete rows
or bypass the precondition to make the migration pass.

Changeset 419 adds version columns to worklist requests and steps. Validate both
changesets against a synthetic copy with the deployed database version, including
duplicate rows, concurrent association, concurrent status changes and rollback.
H2 tests alone do not establish MySQL/MariaDB lock or migration behavior.

Changeset 420 expands comparison details to a large text column so discrepancies
across multiple series remain available for review without truncation or a failed
callback. Validate this migration together with the other two changesets.

Back up OpenMRS and Orthanc as a coordinated recovery point before an authorized
upgrade. An OpenMRS SQL backup does not include Orthanc's object storage and index.

## Validation

The GitHub workflow runs `mvn -B clean verify` with Java 8 for both API and OMOD,
and retains the test reports. The regression suite covers upload boundaries,
invalid Orthanc responses, ownership, idempotence, worklist identity, full and
partial callbacks, strict dates and stale versions.

For this change, local tests, compilation and runtime checks are **NOT RUN** at
the user's request. CI, migration testing and DEV/QLTY acceptance remain pending.
Use synthetic patients and studies only; never use production or identifiable
data for validation.

The legacy `orthanc-plugin/orthancWorklist.py` is not installed by the SIH Salus
imaging profile. It is not part of the hardened API release: it still needs a
separate review of logging, credentials, timeouts, durable notification retries
and cleanup before it can be enabled. Do not treat API regression coverage as
evidence that a modality-to-worklist bridge is operational.

## Upstream background

The original integration was designed for OpenMRS 2.x and Orthanc. Historical
demos are available for [OpenMRS 2.x](https://youtu.be/no3WNaq4Q_M) and
[OpenMRS 3.x](https://youtu.be/Z4MRPmkwHms); they are not deployment instructions
for the current SIH Salus distribution.
