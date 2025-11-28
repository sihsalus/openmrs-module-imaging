import os
import sys
import logging
import random
import pydicom
import pydicom.uid
from pydicom.dataset import Dataset
import uuid

# -----------------------------Test parameters ------------------------------
Accession_Number = "ACC12345"
Study_Instance_UID = pydicom.uid.generate_uid()
Request_Description = f"Test request {uuid.uuid4()}"
Scheduled_Procedure_Step_ID = "1"
Configuration_ID=1
Requesting_Physician = "Dr. Tester"
Study_Description = "Test study"
Series_Description = "Test series description"
Given_Name = "Test"
Family_Name = "Patient"
Gender = "M"
Modality="CT"
Priority="High"
Aet_Title="ORTHANC"
Referring_Physician="Dr. Referring Doctor"
Requested_Step_Description= "Procedure step CT study"
Station_Name="TEST-STATIOM"
Location="Test Room 1"
Step_Status="SCHEDULED"

# Orthanc Configuration data
config_baseURL="http://host.docker.internal:8052"
config_proxyurl="http://localhost:8052"
orthanc_username="orthanc"
orthanc_password="orthanc"

# -----------------------------Logging setup ---------------------------------
def setup_logger(name="e2etest", log_file_path="e2e_test_tool.log"):
    """set up and return a logger with both console and file handler"""

    # Remove old log file
    if os.path.exists(log_file_path):
        os.remove(log_file_path)

    # Logger
    log = logging.getLogger(name)

    # Set the overall logger level to DEBUG to capture all messages
    log.setLevel(logging.DEBUG)

    # Avoid duplicate handlers if called multiple times
    if log.handlers:
        return log

    # Console handler
    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setLevel(logging.INFO)
    console_formatter = logging.Formatter('%(asctime)s [%(levelname)s] %(message)s')
    console_handler.setFormatter(console_formatter)
    log.addHandler(console_handler)

    # File handler (overwrite)
    file_handler = logging.FileHandler(log_file_path, mode='w')
    file_handler.setLevel(logging.DEBUG)
    file_formatter = logging.Formatter('%(asctime)s [%(levelname)s] %(message)s')
    file_handler.setFormatter(file_formatter)
    log.addHandler(file_handler)

    log.info("Logger initialized. Logs go to console and '%s'", log_file_path)
    return log

# --------------------------------Shared logger ------------------------------
logger = setup_logger()

def get_logger(name: str):
    """
     Get a module-specific logger that shares handlers with the main logger.
     Example:
         logger = get_logger("OpenMRSClient")
     """
    main_logger = logging.getLogger("e2etest")
    child_logger = logging.getLogger(name)
    if not child_logger.handlers:
        for handler in main_logger.handlers:
            child_logger.addHandler(handler)
        child_logger.setLevel(main_logger.level)
    return child_logger


def env_or(name: str, default=None):
    """Get environment variable or fallback to default."""
    return os.environ.get(name, default)

def generate_openmrs_id(prefix=""):
    allowed_chars = "0123456789ACDEFGHJKLMNPRTUVWXY"
    base = ''.join(random.choice(allowed_chars) for _ in range(7))  # 7 base chars

    # Compute check digit
    total = 0
    factor = 2
    for char in reversed(base):
        codepoint = allowed_chars.index(char)
        addend = factor * codepoint
        addend = (addend // 30) + (addend % 30)
        total += addend
        factor = 1 if factor == 2 else 2
    remainder = total % 30
    check_codepoint = (30 - remainder) % 30
    check_digit = allowed_chars[check_codepoint]
    return f"{prefix}{base}{check_digit}"

def openmrs_to_dicom_patient_name(patient: dict) -> str:
    """ Convert OpenMRS patient dict to DICOM PatientName (PN) format."""

    person = patient.get('person', {})
    given_name = person.get('givenName')
    family_name = person.get('familyName')

    # Fallback: split display if givenName/familyName not set
    if not given_name or not family_name:
        display = person.get('display', 'Unknown^Patient')
        parts = display.strip().split(" ", 1)
        if len(parts) == 2:
            given_name, family_name = parts[0], parts[1]
        else:
            given_name = parts[0]
            family_name = "Unknown"

    # Return DICOM PN format
    return f"{family_name}^{given_name}"

def make_query_pynetdicom(patientName: str,
               patientID: str,
               level: str,
               studyInstanceUID: str = "",
               seriesInstanceUID: str = "",
               sopInstanceUID: str = "",
               ) -> Dataset:
        """
         Build a DICOM C-FIND query for 'pynetdicom' (STUDY, SERIES, or IMAGE level)
        """
        level = level.upper()
        if level not in ("STUDY", "SERIES", "IMAGE"):
            raise ValueError(f"Invalid QueryRetrieveLevel: {level}")
        ds = Dataset()
        ds.QueryRetrieveLevel = level
        ds.PatientName = patientName
        ds.PatientID = patientID

        if level in ("SERIES", "IMAGE"):
            ds.StudyInstanceUID = studyInstanceUID

        if level=="IMAGE":
            ds.SeriesInstanceUID = seriesInstanceUID
            ds.SOPInstanceUID = sopInstanceUID

        return ds

def make_query_fs(
        patient_name: str,
        patient_id: str,
        level: str,
        study_instance_uid: str = "",
        series_instance_uid: str = "",
        sop_instance_uid: str = ""
) -> dict[str, str]:
    """ Build a C-FIND query dictionary for use with findscu (must be dict with string keys)."""
    
    level = level.upper()
    if level not in ("STUDY", "SERIES", "IMAGE"):
        raise ValueError(f"Invalid QueryRetrieveLevel: {level}")

    query = {
        "PatientName": patient_name,
        "PatientID": patient_id,
        "QueryRetrieveLevel": level
    }

    if level in ("SERIES", "IMAGE"):
        query["StudyInstanceUID"] = study_instance_uid or ""

    if level == "IMAGE":
        query["SeriesInstanceUID"] = series_instance_uid or ""
        query["SOPInstanceUID"] = sop_instance_uid or ""

    return query
