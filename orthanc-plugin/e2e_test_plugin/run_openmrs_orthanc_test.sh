#!/usr/bin/env bash

# OpenMRS + Orthanc end to end test runner

set -e # Exit on first error
set -o pipefail
IFS=$'\n\t'

# ----------------- CONFIG --------------
#OPENMRS_BASE_URL=${OPENMRS_BASE_URL:-http://localhost/openmrs}  # OpeMRS server and frontend in Docker
OPENMRS_BASE_URL=${OPENMRS_BASE_URL:-http://localhost:3030/openmrs} # for local server
OPENMRS_USER=${OPENMRS_USER:-admin}
OPENMRS_PASS=${OPENMRS_PASS:-Admin123}

ORTHANC_HTTP_URL=${ORTHANC_HTTP_URL:-http://localhost:8052}
ORTHANC_DICOM_AE=${ORTHANC_DICOM_AE:-ORTHANC}
ORTHANC_DICOM_HOST=${ORTHANC_DICOM_HOST:-localhost}
ORTHANC_DICOM_PORT=${ORTHANC_DICOM_PORT:-4242}


GIVEN_NAME=${GIVEN_NAME:-Test}
FAMILY_NAME=${FAMILY_NAME:-Patient}
GENDER=${GENDER:-M}

POLL_TIMEOUT=${POLL_TIMEOUT:-60}
POLL_INTERVAL=${POLL_INTERVAL:-5}

# main python detailed log
PY_LOG_FILE="e2e_test_tool.log"
# Shell log
RUN_LOG_FILE="e2e_run_test_tool.log"

# ----------------- CHECK------------------
echo "Checking Python and dependencies..."

if ! command -v python3 >/dev/null 2>&1; then
    echo "Python3 not found. Please install if first." >&2
    exit 1
fi

for pkg in requests pydicom pynetdicom; do
    if ! python3 -c "import $pkg" >/dev/null 2>&1; then
        echo "Installing missing package: $pkg"
        # pip install "$pkg"
    fi
done

# ------------------- RUN Test -----------------
echo "Starting E2E test...."
echo "Logs will be save to $PY_LOG_FILE"
echo "------------------------------------------"

python3 main.py \
  --openmrs "$OPENMRS_BASE_URL" \
  --user "$OPENMRS_USER" \
  --password "$OPENMRS_PASS" \
  --orthanc-http "$ORTHANC_HTTP_URL" \
  --orthanc-dicom-ae "$ORTHANC_DICOM_AE" \
  --orthanc-dicom-host "$ORTHANC_DICOM_HOST" \
  --orthanc-dicom-port "$ORTHANC_DICOM_PORT" \
  --given-name "$GIVEN_NAME" \
  --family-name "$FAMILY_NAME" \
  --gender "$GENDER" \
  --poll-timeout "$POLL_TIMEOUT" \
  --poll-interval "$POLL_INTERVAL" | tee "$RUN_LOG_FILE"

EXIT_CODE=${PIPESTATUS[0]}

# ----------------- RESULTS --------------
if [ $EXIT_CODE -eq 0 ]; then
    echo "Test completed successfully"
    echo "See log file: $PY_LOG_FILE"
else
    echo "Test failed with exit code $EXIT_CODE"
    echo "Check log file for details: $RUN_LOG_FILE"
fi

echo ""
echo "Python log file: $PY_LOG_FILE"
echo "Runner log file: $RUN_LOG_FILE"

exit $EXIT_CODE