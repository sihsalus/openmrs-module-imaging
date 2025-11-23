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

import json
import requests
from datetime import datetime, timezone
from utils import generate_openmrs_id, get_logger

from pydicom.dataset import Dataset as DicomDataSet, FileDataset
from pydicom.sequence import Sequence
from pynetdicom import AE, QueryRetrievePresentationContexts
from pynetdicom.sop_class import StudyRootQueryRetrieveInformationModelFind, ModalityWorklistInformationFind

from utils import (
    Configuration_ID,
    Given_Name,
    Family_Name,
    Gender,
    Modality,
    Priority,
    Requesting_Physician,
    Referring_Physician,
    Requested_Step_Description,
    Station_Name,
    Location,
    Step_Status,
    Aet_Title
)

logger = get_logger("OpenMRSClient")

class OpenMRSClient:
    """Client for interacting with OpenMRS REST API."""
    def __init__(self, base_url: str, username: str, password: str):
        self.base_url = base_url.rstrip('/') + '/ws/rest/v1'
        self.auth = (username, password)
        self.headers = {'Content-Type': 'application/json'}

        # module endpoints base
        self.module_base = base_url.rstrip('/') + '/module/imaging'

    def _get(self, endpoint, **kwargs):
        url = f"{self.base_url}/{endpoint.lstrip('/')}"
        resp = requests.get(url, auth=self.auth, **kwargs)
        resp.raise_for_status()
        return resp.json()

    def get_orthanc_configurations(self):
        """Retrieve Orthanc PACS configurations from OpenMRS."""
        url = f"{self.base_url}/imaging/configurations"
        logger.info(f"Fetching Orthanc configurations from: {url}")
        try:
            resp = requests.get(url, auth=self.auth, headers=self.headers, timeout=10)
            if resp.status_code != 200:
                logger.error("Failed to find configurations: %s", resp.status_code)
                return []
            results = resp.json()
            if isinstance(results, list):
                logger.info(f"Found {len(results)} Orthanc configuration(s).")
                return results
            else:
                logger.warning("Unexpected response format for Orthanc configurations.")
                return []
        except Exception as e:
            logger.error(f"Failed to retrieve Orthanc configuration: {e}")
        return []

    
    def create_orthanc_configuration(self, url, proxyurl, username, password):
        """Create a new Orthanc PACS configuration in OpenMRS."""
        data = {
            "url": url,
            "proxyurl": proxyurl,
            "username": username,
            "password": password
        }
        r = requests.post(
            f"{self.module_base}/storeConfiguration.form",
            auth=self.auth,
            data=data
        )

        if r.status_code in (200, 302):
            logger.info("Orthanc configuration created successfully in OpenMRS.")
            return True
        logger.warning(f"Failed to create Orthanc configuration: {r.status_code}")
        return False

    def create_patient(self, given_name: str = Given_Name, family_name: str = Family_Name, gender: str = Gender, birthdate: str = None):
        """Create a new patient in OpenMRS."""
        if birthdate is None:
            birthdate = datetime.now(timezone.utc).strftime('%Y-%m-%dT00:00:00.000%z')

        identifier_type_uuid = "05a29f94-c0ed-11e2-94be-8c13b969e334"  # OpenMRS ID
        location_uuid = "8d6c993e-c2cc-11de-8d13-0010c6dffd0f"

        # Generate a random identifier
        identifier_value = generate_openmrs_id()
        logger.info(f"Generated OpenMRS ID: {identifier_value}")

        payload = {
            "person": {
                "names": [{"givenName": given_name, "familyName": family_name}],
                "gender": gender,
                "birthdate": birthdate
            },
            "identifiers": [
                {
                    "identifier": identifier_value,
                    "identifierType": identifier_type_uuid,
                    "location": location_uuid,
                    "preferred": True
                }
            ]
        }
        url = f"{self.base_url}/patient"
        logger.info(f"Create patient URL: {url}")

        resp = requests.post(url, auth=self.auth, headers=self.headers, data=json.dumps(payload))
        if resp.status_code >= 400:
            logger.error("Failed to create patient: %s", resp.text)

        resp.raise_for_status()
        patient = resp.json()
        logger.info("Created patient %s %s (%s)", given_name, family_name, patient.get("uuid"))
        return patient

    def delete_patient(self, patient_uuid: str):
        """Delete a patient from OpenMRS."""
        url = f"{self.base_url}/patient/{patient_uuid}?purge=true"
        resp = requests.delete(url, auth=self.auth, headers=self.headers)
        if resp.status_code in [200, 204]:
            logger.info("Deleted patient %s", patient_uuid)
        else:
            logger.warning("Failed to delete patient %s: %s", patient_uuid, resp.status_code)


    def search_patient(self, given_name=Given_Name, family_name=Family_Name, gender=Gender):
        """Search for patients in OpenMRS"""
        query = f"{given_name} {family_name}"
        url = f"{self.base_url}/patient"
        params = {"q": query}

        logger.info("Searching patients: %s?q=%s", url, query)
        resp = requests.get(url, auth=self.auth, headers=self.headers, params=params)
        if resp.status_code != 200:
            logger.error("Failed to search patients: %s", resp.text)
            return []

        results = resp.json().get("results", [])
        filtered = []
        for patient in results:
            p_uuid = patient["uuid"]
            # Fetch patient details
            details_resp = requests.get(f"{self.base_url}/patient/{p_uuid}", auth=self.auth, headers=self.headers)
            if details_resp.status_code != 200:
                logger.warning("Failed to fetch patient %s details: %s", p_uuid, details_resp.text)
                continue
            details = details_resp.json()
            person = details.get("person", [])
            display = person.get("display", "").strip()

            name_match = display.lower() == f"{given_name} {family_name}".lower()
            gender_match = person.get("gender", "").upper() == gender.upper()

            if  name_match and gender_match:
                filtered.append({'name': person['display'], 'uuid': p_uuid})

        logger.info("search_patient: found %d matching patients", len(filtered))
        return filtered

    def create_requestProcedure(self,
                                patient_uuid: str,
                                accession_number: str,
                                study_instance_uid: str,
                                request_description: str,
                                requesting_physician: str = Requesting_Physician,
                                priority: str = Priority,
                                configuration_id: int = Configuration_ID
                                ):
        """
        Create a new request procedure in OpenMRS for the given patient for worklist.
        """
        url_pr = f"{self.base_url}/worklist/saverequest"
        logger.info("Create Request Procedure URL %s" % url_pr)
        payload_pr = {
            "patientUuid": patient_uuid,
            "accessionNumber": accession_number,
            "studyInstanceUID": study_instance_uid,
            "requestDescription": request_description,
            "requestingPhysician": requesting_physician,
            "priority": priority,
            "configurationId": configuration_id
        }
        try:
            resp = requests.post(url_pr, auth=self.auth, headers=self.headers, data=json.dumps(payload_pr))
            logger.info(f"Create request procedure response: {resp.status_code}")
        except requests.RequestException as e:
            logger.error(f"HTTP request failed: {e}")
            return {"status": False, "message": f"HTTP request failed: {e}"}

        if not resp.ok:
            logger.error(f"Failed to create RequestProcedure: {resp.status_code} - {resp.text}")
            return {"status": False, "message": f"HTTP error {resp.status_code}"}

        if resp.text:
            try:
                data = resp.json()
            except ValueError:
                logger.warning("Response is not valid JSON: %s", resp.text)
                data = {"status": True, "message": resp.text}
        else:
            data = {"status": True, "message": "RequestProcedure created (empty response)"}

        return data

    def delete_requestProcedure(self, procedure_id: str):
        """Delete a request procedure (worklist) from OpenMRS."""

        url = f"{self.base_url}/worklist/request"
        params = {"requestId": procedure_id}
        resp = requests.delete(url, auth=self.auth, headers=self.headers,  params=params)
        if resp.status_code not in [200, 204]:
            logger.warning(f"Failed to delete request procedure {procedure_id}: {resp.status_code}")
        else:
            logger.info(f"Deleted request procedure: {procedure_id}")

    def get_procedures_by_patient(self, patient_uuid: str):
        """Retrieve request procedures for a specific patient from OpenMRS."""

        url = f"{self.base_url}/worklist/patientrequests"
        logger.info("Get Procedures URL %s" % url)
        params = {'patient': patient_uuid}
        resp = requests.get(url, auth=self.auth, headers=self.headers, params=params)
        if resp.status_code >= 400:
            logger.error(f"Failed to get procedures: {resp.text}")
            resp.raise_for_status()
        resp_json = resp.json()
        return resp_json

    def get_procedures_by_status(self, status: str = 'all'):
        """Retrieve request procedures by status from OpenMRS."""

        url = f"{self.base_url}/worklist/requests?status={status}"
        logger.info("Get Procedures by status URL %s" % url)
        resp = requests.get(url, auth=self.auth, headers=self.headers)
        logger.info(f"Response status code for retrieving procedures by satus = {status}: {resp.status_code}")
        if resp.status_code >= 400:
            logger.error(f"Failed to get procedures: {resp.text}")
            resp.raise_for_status()
        resp_json = resp.json()
        return resp_json

    def create_requestProcedureStep(self,
                                    request_id: int,
                                    modality: str = Modality,
                                    aet_title: str = Aet_Title,
                                    referring_physician: str = Referring_Physician,
                                    requested_step_description: str = Requested_Step_Description,
                                    station_name: str = Station_Name,
                                    location: str = Location,
                                    step_status: str = Step_Status
                                    ):
        """
        Create a procedure step for an existing request procedure.
        org.openmrs.module.imaging.api.worklist.RequestProcedureStep fields.
        """
        url = f"{self.base_url}/worklist/savestep"

        now = datetime.now(timezone.utc)
        payload = {
            "requestId": request_id,
            "modality": modality,
            "aetTitle": aet_title,
            "scheduledPerformingPhysician": referring_physician,
            "requestedProcedureDescription": requested_step_description,
            "stepStartDate": now.strftime("%Y-%m-%d"),
            "stepStartTime": now.strftime("%H:%M:%S"),
            "performedProcedureStepStatus": step_status,
            "stationName": station_name,
            "procedureStepLocation": location
        }

        try:
            # Send the POST request
            response = requests.post(url, auth=self.auth, headers=self.headers, data=json.dumps(payload))
            logger.info(f"Create procedure step response: {response.status_code}")
        except requests.RequestException as e:
            logger.error(f"HTTP request failed: {e}")
            return {"status": False, "message": f"HTTP request failed: {e}"}

        if not response.ok:
            logger.error(f"Procedure step creation failed: {response.status_code} - {response.text}")
            return {"status": False, "message": f"HTTP error {response.status_code}"}


        # Parse JSON response
        try:
            res_json = response.json()
        except ValueError:
            if response.ok and not response.text.strip():
                # Treat empty HTTP 200 as success
                logger.info("Procedure step created successfully (no JSON returned)")
                return {"status": True, "message": "Step created"}
            else:
                logger.error("Failed to parse JSON from response; step not created")
                return {"status": False, "message": "Step is not created"}

        if res_json.get("status", False):
            logger.info(f"Procedure step created successfully: {res_json}")
        else:
            logger.error(f"Procedure step creation failed: {res_json.get('message', 'No message from server')}")


        return res_json

    def get_steps_by_request(self, request_id):
        """Retrieve procedure steps for a specific request procedure from OpenMRS."""

        url = f"{self.base_url}/worklist/requeststep?requestId={request_id}"
        response = requests.get(url, auth=self.auth)
        response.raise_for_status()
        steps = response.json()
        logger.info(f"Found {len(steps)} procedure steps for request {request_id}")
        return steps


    def update_procedure_step_status(self, step_uuid: str, new_status: str = "COMPLETE"):
        """Update the status of a procedure step in OpenMRS."""
        url = f"{self.base_url}/worklist/updaterequeststatus"
        payload = {"uuid": step_uuid, "performedProcedureStepStatus": new_status}
        logger.info(f"Updating procedure step {step_uuid} status to {new_status}")
        resp = requests.post(url, auth=self.auth, headers=self.headers, data=json.dumps(payload))
        logger.info(f"Update procedures step status response: {resp}")
        resp.raise_for_status()
        return resp.json()

    def delete_procedure_Step(self, step_id: str):
        """Delete a procedure step from request procedure of OpenMRS."""
        url = f"{self.base_url}/worklist/requeststep"
        params = {"stepId": step_id}
        resp = requests.delete(url, auth=self.auth, headers=self.headers, params=params)
        if resp.status_code not in [200, 204]:
            logger.warning(f"Failed to delete request procedure step {step_id}: {resp.status_code}")
        else:
            logger.info(f"Deleted procedure step: {step_id}")

    def get_studies(self, patient_uuid: str):
        """Retrieve imaging studies for a specific patient from OpenMRS."""

        url = f"{self.base_url}/imaging/studies?patient={patient_uuid}"
        resp = requests.get(url, auth=self.auth, headers=self.headers)
        resp.raise_for_status()
        return resp.json()

    def get_study_id_by_uid(self, patient_id: str, study_instance_uid: str):
        """Retrieve the study ID for a specific study instance UID of a patient from OpenMRS."""
        studies = self.get_studies(patient_id)
        for study in studies:
            if study.get("studyInstanceUID") == study_instance_uid:
                return study.get("id")
        return None

    def delete_study(self, study_id: int, delete_option: str = "full"):
        """Delete an imaging study from OpenMRS.
        delete_option: "full" or "openmrs_only"
        """
        url = f"{self.base_url}/imaging/study"
        params = {"studyId": study_id, "deleteOption": delete_option}
        resp = requests.delete(url, params=params)
        resp.raise_for_status()
        logger.info("Deleted study %s with option '%s'", study_id, delete_option)


    def delete_all_studies_for_patient(self, patient_uuid: str, delete_option="full"):
        """Delete all imaging studies for a specific patient from OpenMRS."""
        try:
            studies = self.get_studies(patient_uuid)

            if not studies:
                logger.info(f"No studies found for patient {patient_uuid}")
                return
            for study in studies:
                study_id = study.get("id")
                study_uid = study.get("studyInstanceUID")
                logger.info(f"Deleting study {study_id} (UID={study_uid}) with option '{delete_option}'")
                # Call your API to delete the study
                try:
                    self.delete_study(study_id, delete_option=delete_option)
                except Exception as e:
                    logger.warning(f"Failed to delete study {study_id}: {e}")

            logger.info(f"All studies for patient {patient_uuid} deleted")
        except Exception as e:
            logger.exception(f"Error deleting studies for patient {patient_uuid}: {e}")
