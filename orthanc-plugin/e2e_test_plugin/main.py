# This file is part of [Integration of Orthanc with OpenMRS].
#
# Integration of Orthanc with OpenMRS is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# Integration of Orthanc with OpenMRS is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with [Integration of Orthanc with OpenMRS]. If not, see <https://www.gnu.org/licenses/>.

import argparse
import time

from utils import env_or, logger, make_query_pynetdicom, make_query_fs
from openmrs_client import OpenMRSClient
from orthanc_client import OrthancClient
import pydicom
from pydicom.dataset import Dataset, FileDataset
from pydicom.sequence import Sequence
from pynetdicom import AE, QueryRetrievePresentationContexts
from pynetdicom.sop_class import StudyRootQueryRetrieveInformationModelFind, ModalityWorklistInformationFind

from utils import (
    Accession_Number,
    Study_Instance_UID,
    Request_Description,
    Scheduled_Procedure_Step_ID,
    Given_Name,
    Family_Name,
    Modality,
    Priority,
    config_baseURL,
    config_proxyurl,
    orthanc_username,
    orthanc_password
)

# --------------------------------- Main Test Logic ----------------------------
def run_test(run_args):
    """ 
    Run the end-to-end integration to test data flwow between OpenMRS and Orthanc.
    """
    logger.info("Starting integration test with OpenMRS (%s) and orthanc (%s)",
                run_args.openmrs, run_args.orthanc_http)
    patient_uuid = None
    procedure_id = None

    openmrs = OpenMRSClient(run_args.openmrs, run_args.user, run_args.password)
    orthanc = OrthancClient(run_args.orthanc_http,
                            dicom_ae=run_args.orthanc_dicom_ae,
                            dicom_host=run_args.orthanc_dicom_host,
                            dicom_port=run_args.orthanc_dicom_port)
    try:
        # Create only one patient
        patients = openmrs.search_patient(run_args.given_name, run_args.family_name, run_args.gender)
        for p in patients:
            openmrs.delete_patient(p)

        if not patients:
            patient = openmrs.create_patient(run_args.given_name, run_args.family_name, run_args.gender)
        else:
            # Use the first patient found
            patient = patients[0]

        if not patient:
            logger.error("Failed to find or create the patient")
            return

        patient_uuid = patient["uuid"]
        person = patient.get("person")
        patient_id = patient_uuid[:8]  # first 8 chars of OpenMRS UUID

        if person:
            patient_name = person.get("display")
        else:
            patient_name = f"{run_args.given_name} {run_args.family_name}"

        logger.info("Using patient: %s (%s)", patient_name, patient_uuid)

        # Verify Orthanc configuration
        configs = openmrs.get_orthanc_configurations()
        if configs==[] or len(configs) == 0 or configs is None:
            logger.warning("No Orthanc configuration found.")
            openmrs.create_orthanc_configuration(config_baseURL, config_proxyurl, orthanc_username, orthanc_password)
            logger.warning("Added a new Orthanc configuration")
            configs = openmrs.get_orthanc_configurations()
        else:
            logger.info("OpenMRS-Orthanc integration configuration confirmed")

        # Create request procedure for the patient
        request_procedure = openmrs.create_requestProcedure(
            patient_uuid,
            Accession_Number,
            Study_Instance_UID,
            Request_Description,
            priority=Priority,
            configuration_id=configs[0]["id"]
        )

        # --- Fetch the newly created request to get its numeric ID ---
        try:
            # Fetch procedure by status
            procedure_scheduled_list = openmrs.get_procedures_by_status("scheduled")
            if procedure_scheduled_list:
                logger.info(f"Get procedures by status 'scheduled': {procedure_scheduled_list} {len(procedure_scheduled_list)}")
            procedure_progress_list = openmrs.get_procedures_by_status("progress")
            if procedure_progress_list:
                logger.info(f"Get procedures by status 'progress': {procedure_progress_list}")
            procedure_complete_list = openmrs.get_procedures_by_status()
            if procedure_complete_list:
                logger.info(f"Get procedures by status 'progress': {procedure_complete_list}")

            # Fetch procedures by patient
            procedure_list = openmrs.get_procedures_by_patient(patient_uuid)
            logger.info(f"Get procedures by patient: {procedure_list}")
            if not procedure_list:
                raise RuntimeError("No request procedures found for this patient after creation.")

            # Assuming the last one is the newest
            latest_proc = procedure_list[-1]
            procedure_id = latest_proc.get("id") or latest_proc.get("requestId")
            logger.info(f"Fetched procedure ID: {procedure_id}")

            if not procedure_id:
                raise RuntimeError(f"Could not find request ID in response: {latest_proc}")

        except Exception as e_procedure:
            logger.exception(f"Failed to fetch created request procedure: {e_procedure}")
            raise

        # --- Create procedure step ---
        procedure_step = openmrs.create_requestProcedureStep(procedure_id)
        if procedure_step is None:
            logger.error("Failed to create procedure step")
        else:
            logger.info("Procedure step created successfully: %s", procedure_step)

        steps = openmrs.get_steps_by_request(procedure_id) or []
        logger.info(f"Number of steps for procedure {procedure_id}: {len(steps)}")

        # --------------------Orthanc worklist query ----------------------------
        query = Dataset()
        query.PatientName = patient_name
        query.AccessionNumber = Accession_Number
        study_instance_uid = ""
        series_instance_uid = ""

        results = orthanc.find_worklist(query)
        logger.info(f"Found worklist items: {len(results)}")
        for r in results:
            logger.info(f"Found worklist item: {r}")

        # --- Generate fake DICOM study ---
        logger.info("Generating fake DICOM for patient %s", patient_name)
        study_data = orthanc.create_dicom_study(
            patient_name=patient_name,
            patient_id=patient_id,
            modality=Modality,
            series_count=2,
            instances_per_series=3,
            scheduled_procedure_step_id=Scheduled_Procedure_Step_ID
        )

        # --- Save one instance as example ----
        with open("example_instance.dcm", "wb") as f:
            f.write(list(study_data.values())[0][0])

        # --- Upload each DICOM instance to Orthanc ---
        for series_name, instances in study_data.items():
            for idx, dicom_bytes in enumerate(instances):
                try:
                    response = orthanc.upload_instance(dicom_bytes)
                    logger.info(
                        "Upload %s instance %d/%d orthanc ID=%s",
                        series_name, idx+1, len(instances), response.get("ID")
                    )
                except Exception as e_series:
                    logger.error(
                        "Failed to upload DICOM instance %d in %s: %s",
                        idx+1, series_name, e_series
                    )

        #---- Perform C-FIND to check Orthanc has the study -----------------
        logger.info("Performing C-FIND to verify study existence in Orthanc")

        logger.info("--- STUDY level C-FIND using pynetdicom ----")
        study_query_pynetdicom=make_query_pynetdicom(patient_name, patient_id, "STUDY")
        try:
            studies = orthanc.cfind_study(study_query_pynetdicom, use_findscu=False)
            for status, identifier in studies:
                if identifier:
                    study_instance_uid = getattr(identifier, "StudyInstanceUID", None)
                    logger.info(f"Study found C-FIND (pynetdicom): {study_instance_uid}")
        except Exception as e_study:
            logger.error(f"Study C-FIND (pynetdicom) failed: {e_study}")

        logger.info("--- STUDY Level C-FIND using findscu ----")
        study_query_fs=make_query_fs(patient_name, patient_id, "STUDY")
        try:
            output = orthanc.cfind_study(study_query_fs, use_findscu=True)
            logger.info(f"Study findscu output: \n{output}")
        except Exception as e_study_findscu:
            logger.error(f"Study C-FIND (findscu) failed: {e_study_findscu}")

        # ------------------- SERIES Level C-FIND -------------------
        logger.info("--- SERIES level C-FIND using pynetdicom ----")
        series_query_pynetdicom = make_query_pynetdicom(patient_name, patient_id, "SERIES", studyInstanceUID=study_instance_uid)
        try:
            series_list = orthanc.cfind_study(series_query_pynetdicom, use_findscu=False)
            for status, identifier in series_list:
                if identifier:
                    series_instance_uid = getattr(identifier, "SeriesInstanceUID", None)
                    logger.info(f"Series C-FIND (pynetdicom): {series_instance_uid}")
        except Exception as e_series:
            logger.error(f"Series C-FIND (pynetdicom) failed: {e_series}")

        logger.info("--- SERIES level C-FIND using findscu ----")
        series_query_fs = make_query_fs(patient_name, patient_id, "SERIES", study_instance_uid)
        try:
            series_output = orthanc.cfind_study(series_query_fs, use_findscu=True)
            logger.info(f"Series C-FIND (findscu) output:\n{series_output}")
        except Exception as e_series_fs:
            logger.error(f"Series C-FIND (findscu) failed: {e_series_fs}")

        # ------------------- INSTANCE Level C-FIND -------------------
        logger.info("--- INSTANCE Level C-FIND using pynetdicom ---- ")
        instance_query_pynetdicom= make_query_pynetdicom(
            patient_name,
            patient_id,
            "IMAGE",
            studyInstanceUID=study_instance_uid,
            seriesInstanceUID=series_instance_uid
        )
        try:
            instances = orthanc.cfind_study(instance_query_pynetdicom, use_findscu=False)
            for status, identifier in instances:
                if identifier:
                    sop_uid = getattr(identifier, "SOPInstanceUID", None)
                    logger.info(f"Instances C-FIND (pynetdicom): {sop_uid}")
        except Exception as e_instance:
            logger.error(f"Instance C-FIND (pynetdicom) failed: {e_instance}")

        logger.info("--- INSTANCES level C-FIND using findscu ----")
        instance_query_fs= make_query_fs(
            patient_name,
            patient_id,
            "IMAGE",
            study_instance_uid,
            series_instance_uid
        )
        try:
            instance_output = orthanc.cfind_study(instance_query_fs, use_findscu=True)
            logger.info(f"Instances C-FIND (findscu) output: \n{instance_output}")
        except Exception as e_instance_fs:
            logger.error(f"Series C-FIND (findscu) failed: {e_instance_fs}")

    except Exception as e_e2e_test:
        logger.exception("E2E test failed with exception: %s", e_e2e_test)
        raise
    finally:
        logger.info("Starting cleanup...")
        try:
            if patient_uuid:
                try:
                    # Delete all studies created for this patient
                    openmrs.delete_all_studies_for_patient(patient_uuid, delete_option="full")
                except Exception as e:
                    logger.warning("Failed to delete OpenMRS studies: %s", e)

                try:
                    orthanc.delete_all_studies_in_orthanc()
                except Exception as e_deleteOrthanc:
                    logger.warning(f"Failed to delete Orthanc studies: %s {e_deleteOrthanc}")

                if procedure_id:
                    try:
                        procedure_steps = openmrs.get_steps_by_request(procedure_id)
                        for step in procedure_steps:
                            step_id = step.get('id') or step.get('uuid')
                            if step_id:
                                openmrs.delete_procedure_Step(step_id)
                    except Exception as e_procedureStep:
                        logger.warning(f"Failed to delete procedure steps: {e_procedureStep}")

                # Delete procedures
                try:
                    procedures = openmrs.get_procedures_by_patient(patient_uuid)
                    for procedure in procedures:
                        proc_id = procedure.get('id')
                        if proc_id:
                            openmrs.delete_requestProcedure(proc_id)
                except Exception as e_delete_procedure:
                    logger.warning(f"Failed to delete procedures: {e_delete_procedure}")

                # Delete patient
                try:
                    openmrs.delete_patient(patient_uuid)
                    time.sleep(2)
                    still_exists = openmrs.search_patient(run_args.given_name, run_args.family_name, run_args.gender)
                    if not still_exists:
                        logger.info("Patient successfully deleted and not found in OpenMRS")
                    else:
                        logger.warning("Patient still exists after deletion: %s", still_exists)

                    logger.info("Cleanup completed successfully.")
                except Exception as e_deletePatient:
                    logger.warning("Failed to delete patient: %s", e_deletePatient)
            else:
                logger.warning("Cleanup test data: No patient UUID")

        except Exception as cleanup_err:
            logger.exception("Error during cleanup: %s", cleanup_err)

# -------------Main / pytest support -------------------
if __name__ == '__main__':

    ap = argparse.ArgumentParser(description='OpenMRS-Orthanc test tool with cleanup')
    ap.add_argument('--openmrs', default=env_or('OPENMRS_BASE_URL', 'http://localhost:8080/openmrs'))
    ap.add_argument('--user', default=env_or('OPENMRS_USER', 'admin'))
    ap.add_argument('--password', default=env_or('OPENMRS_PASS', 'Admin123'))
    ap.add_argument('--orthanc-http', default=env_or('ORTHANC_HTTP_URL', 'http://localhost:8052'))
    ap.add_argument('--orthanc-dicom-ae', default=env_or('ORTHANC_DICOM_AE', 'ORTHANC'))
    ap.add_argument('--orthanc-dicom-host', default=env_or('ORTHANC_DICOM_HOST', 'localhost'))
    ap.add_argument('--orthanc-dicom-port', type=int, default=int(env_or('ORTHANC_DICOM_PORT', '4242')))
    ap.add_argument('--given-name', default=Given_Name)
    ap.add_argument('--family-name', default=Family_Name)
    ap.add_argument('--gender', default='M')
    ap.add_argument('--poll-timeout', type=int, default=60)
    ap.add_argument('--poll-interval', type=int, default=5)
    args = ap.parse_args()

    logger.info("Parsed arguments: %s", vars(args))

    try:
        run_test(args)

    except Exception as e:
        logger.exception("Integration test failed: %s", e)