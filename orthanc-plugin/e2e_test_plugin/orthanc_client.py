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

import os
import subprocess
import shutil
import tempfile
import pydicom
from pydicom.dataset import FileDataset
import datetime
import requests
from datetime import datetime, timezone
from io import BytesIO
from typing import Optional, List, Dict
from utils import get_logger

from pydicom.dataset import Dataset as DicomDataSet, FileDataset
from pydicom.sequence import Sequence
from pynetdicom import AE, QueryRetrievePresentationContexts
from pynetdicom.sop_class import StudyRootQueryRetrieveInformationModelFind, ModalityWorklistInformationFind

from utils import (
    Accession_Number,
    Scheduled_Procedure_Step_ID,
    Modality,
    Requesting_Physician,
    Study_Description,
    Aet_Title,
)

logger = get_logger("OrthancClient")

class OrthancClient:
    """ Client for interacting with Orthanc via DICOM services and HTTP APIs. """
    def __init__(self, http_url: str,
                 dicom_ae: str = Aet_Title,
                 dicom_host: str = 'localhost',
                 dicom_port: int = 4242):
        self.http_url = http_url.rstrip('/')
        self.dicom_ae = dicom_ae
        self.dicom_host = dicom_host
        self.dicom_port = dicom_port

    # ----------- DICOM C-FIND Query ------------------------------------------------------------
    def cfind_study(self, query: dict,
                    ae_title: str = Aet_Title,
                    use_findscu: bool = False):
        """
        Perform a C-FIND query to Orthanc for studies matching the query dataset.
        """
        logger.info("Performing C-FIND query (method=%s)...",
                    "findscu" if use_findscu else "pynetdicom")

        logger.info("C-FIND query dataset: %s", query)

        if use_findscu:
            return self._cfind_with_findscu(query, ae_title)
        return self._cfind_with_pynetdicom(query, ae_title)

    def _cfind_with_pynetdicom(self, query: dict, ae_title: str):
        """
        Perform C-FIND using the pynetdicom library.
        """
        logger.info("Using pynetdicom C-FIND to %s:%s (AE=%s)",
                     self.dicom_host, self.dicom_port, self.dicom_ae)

        ae = AE(ae_title=ae_title)
        for cx in QueryRetrievePresentationContexts:
            ae.add_requested_context(cx.abstract_syntax)
        assoc = ae.associate(self.dicom_host, self.dicom_port, ae_title=self.dicom_ae)

        results = []
        if assoc.is_established:
            logger.info("DICOM association established with Orthanc AE: %s", self.dicom_ae)
            response = assoc.send_c_find(query,
                                         StudyRootQueryRetrieveInformationModelFind)
            for (status, identifier) in response:
                if status:
                    logger.info("C-FIND status: 0x%04x", status.Status)
                if identifier:
                    logger.info("C-FIND identifier: %s", identifier)
                results.append((status, identifier))
            assoc.release()
            logger.info("C-FIND association released normally.")
        else:
            logger.error("Failed to associate with Orthanc at %s:%s (AE=%s)",
                         self.dicom_host, self.dicom_port, self.dicom_ae)
            raise ConnectionError(f"Could not associate to {self.dicom_host}:{self.dicom_port} "
                                  f"as AE {self.dicom_ae}")

        return results

    def _cfind_with_findscu(self, query: dict, ae_title):
        """
        Perform C-FIND using the external DCMTK findscu binary
        """
        findscu_path = shutil.which("findscu")
        if not findscu_path:
            logger.error("findscu executable not found in PATH.")
            raise FileNotFoundError("findscu executable not found in PATH")

        logger.info("findscu found at: %s", findscu_path)

        # Build temporary DICOM dataset for query
        with tempfile.NamedTemporaryFile(suffix='.dcm', delete=False) as tmp:
            tmp_path = tmp.name
            ds = self._make_dicom_query_dataset(query)

            # Create minimal FileMeta
            file_meta = pydicom.Dataset()
            file_meta.TransferSyntaxUID = pydicom.uid.ImplicitVRLittleEndian

            # wrap original dataset as FileDataset
            ds_file = FileDataset(tmp_path, ds, file_meta=file_meta, preamble=b"\0" * 128)
            ds_file.is_little_endian = True
            ds_file.is_implicit_VR = True

            # Save to temporary file
            ds_file.save_as(tmp_path)
            logger.info("Temporary query dataset saved ot %s", tmp_path)

            cmd = [
                findscu_path,
                '-v',
                '-aec', self.dicom_ae,
                '-aet', ae_title,
                self.dicom_host,
                str(self.dicom_port),
                '-k', f'PatientName={query["PatientName"]}',
                '-k', f'PatientID={query["PatientID"]}',
                '-k', f'QueryRetrieveLevel={query["QueryRetrieveLevel"]}'
            ]
            logger.info("Running findscu command: %s", " ".join(cmd))
            process = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            stdout, stderr = process.communicate()

            os.remove(tmp_path)
            logger.debug("Removed temporary dataset: %s", tmp_path)

            output = stdout.decode() or stderr.decode()
            if process.returncode != 0:
                logger.error("findscu failed (code=%d): %s", process.returncode, output)
                raise RuntimeError(f"findscu failed ({process.returncode}): {output}")

            logger.info("findscu executed successfully.")
            logger.debug("findscu output: %s", output)
            return output

    def _make_dicom_query_dataset(self, query: dict):
        """Construct a DICOM dataset for C-FIND query from a dictionary."""
        ds = pydicom.Dataset()
        for key, value in query.items():
            setattr(ds, key, value)
        logger.debug("Constructed DICOM query dataset with %d elements.", len(query))
        return ds


    def find_worklist(self, query):
        """
        Fetch the created modality worklist
        """
        ae = AE()
        ae.add_requested_context(ModalityWorklistInformationFind)

        assoc = ae.associate(self.dicom_host, self.dicom_port, ae_title=self.dicom_ae)
        if not assoc.is_established:
            raise ConnectionError("Association failed with Orthanc MWL SCP")

        logger.info(query)
        responses = assoc.send_c_find(query, ModalityWorklistInformationFind)
        results = []
        for (status, identifier) in responses:
            if status and status.Status in (0xFF00, 0xFF01):
                results.append(identifier)
        assoc.release()
        return results

    def upload_instance(self, dcm_bytes: bytes):
        """ Upload a single DICOM instance to Orthanc"""
        url = f"{self.http_url}/instances"
        headers = {'Content-Type': 'application/dicom'}
        logger.info("Uploading DICOM instace to %s", url)

        response = requests.post(url, data=dcm_bytes, headers=headers)
        if response.status_code >= 400:
            logger.error("Failed to upload DICOM instance: %s %s", response.status_code, response.text)
        else:
            logger.info("DICOM instance uploaded successfully (status=%s)", response.status_code)

        response.raise_for_status()
        return response.json()

    def delete_all_studies_in_orthanc(self):
        """ Delete all studies from Orthanc """

        # 1. Get all study IDs from Orthanc
        url = f"{self.http_url}/studies"
        logger.info("Fetching all studies from Orthanc: %s", url)
        resp = requests.get(url)
        resp.raise_for_status()
        study_ids = resp.json()  # list of study IDs (strings)
        logger.info("Found %d studies in Orthanc", len(study_ids))

        # 2. Delete each study
        for study_id in study_ids:
            del_url = f"{self.http_url}/studies/{study_id}"
            logger.info("Deleting study %s from Orthanc", study_id)
            del_resp = requests.delete(del_url)
            if del_resp.status_code in [200, 204]:
                logger.info("Study %s deleted successfully", study_id)
            else:
                logger.warning("Failed to delete study %s: %s %s", study_id, del_resp.status_code, del_resp.text)

    # ------------------------------DICOM creation --------------------------
    def create_fake_dicom(
            self,
            patient_name: str,
            patient_id: str,
            modality:str = Modality,
            study_instance_uid: Optional[str] = None,
            series_instance_uid: Optional[str] = None,
            sop_instance_uid: Optional[str] = None,
            scheduled_procedure_step_id: Optional[str] = None,
    ) -> bytes:

        logger.info("Creating fake DICOM for patient '%s' (ID=%s, Modality=%s)",
                    patient_name, patient_id, modality)

        if study_instance_uid is None:
            study_instance_uid = pydicom.uid.generate_uid()
            logger.debug("Generated new StudyInstanceUID: %s", study_instance_uid)

        if series_instance_uid is None:
            series_instance_uid = pydicom.uid.generate_uid()

        if sop_instance_uid is None:
            sop_instance_uid = pydicom.uid.generate_uid()

        file_meta = pydicom.dataset.FileMetaDataset()
        file_meta.MediaStorageSOPClassUID = pydicom.uid.SecondaryCaptureImageStorage
        file_meta.MediaStorageSOPInstanceUID = sop_instance_uid
        file_meta.ImplementationClassUID = pydicom.uid.generate_uid()

        logger.debug(
            "File Meta prepared: MediaStorageSOPClassUID=%s, "
            "MediaStorageSOPInstanceUID=%s, ImplementationClassUID=%s",
            file_meta.MediaStorageSOPClassUID,
            file_meta.MediaStorageSOPInstanceUID,
            file_meta.ImplementationClassUID
        )

        ds = FileDataset(None, {}, file_meta=file_meta, preamble=b"\0" * 128)
        ds.PatientName = patient_name
        ds.PatientID = patient_id
        ds.StudyInstanceUID = study_instance_uid
        ds.SeriesInstanceUID = series_instance_uid
        ds.SOPInstanceUID = sop_instance_uid
        ds.Modality = modality
        ds.StudyDate = datetime.now(timezone.utc).strftime('%Y%m%d')
        ds.StudyTime = datetime.now(timezone.utc).strftime('%H%M%S')
        ds.AccessionNumber = Accession_Number
        ds.Study_Description = Study_Description
        ds.RequestingPhysician = Requesting_Physician
        ds.SeriesDate = datetime.now(timezone.utc).strftime('%Y%m%d')
        ds.SeriesTime = datetime.now(timezone.utc).strftime('%H%M%S')

        logger.debug(
            "DICOM dataset initialized: SeriesInstanceUID=%s, SOPInstanceUID=%s, "
            "StudyDate=%s, StudyTime=%s",
            ds.SeriesInstanceUID,
            ds.SOPInstanceUID,
            ds.StudyDate,
            ds.StudyTime
        )

        ds.ScheduledProcedureStepID = scheduled_procedure_step_id
        ds.Rows = 32
        ds.Columns = 32
        ds.BitsAllocated = 8
        ds.SamplesPerPixel = 1
        ds.is_little_endian = True
        ds.is_implicit_VR = True
        ds.PixelData = b"\x00" * (ds.Rows * ds.Columns)

        logger.debug(
            "Pixel data added: %dx%d (%d bytes)",
            ds.Rows, ds.Columns, len(ds.PixelData)
        )

        bio = BytesIO()
        ds.save_as(bio, write_like_original=False)

        logger.debug(
            "DICOM instance created: StudyUID=%s, SeriesUID=%s, SOPUID=%s",
            ds.StudyInstanceUID, ds.SeriesInstanceUID, ds.SOPInstanceUID
        )

        return bio.getvalue()

    def create_dicom_study(
            self,
            patient_name: str,
            patient_id: str,
            modality: str = Modality,
            series_count: int = 2,
            instances_per_series: int = 3,
            scheduled_procedure_step_id: Optional[str] = Scheduled_Procedure_Step_ID
    ) -> Dict[str, List[bytes]]:
        """ Create a complex DICOM study with multiple series and instances."""
        logger.info(
            "Creating complex DICOM study: patient=%s, series=%d, instances/series=%d",
            patient_name, series_count, instances_per_series
        )

        study_uid = pydicom.uid.generate_uid()
        study = {}

        for series_index in range(series_count):
            series_uid = pydicom.uid.generate_uid()
            series_instances = []

            for instance_index in range(instances_per_series):
                sop_uid = pydicom.uid.generate_uid()
                dicom_bytes = self.create_fake_dicom(
                    patient_name=patient_name,
                    patient_id=patient_id,
                    modality=modality,
                    study_instance_uid=study_uid,
                    series_instance_uid=series_uid,
                    sop_instance_uid=sop_uid,
                    scheduled_procedure_step_id=scheduled_procedure_step_id,
                )

                # append inside the inner loop
                series_instances.append(dicom_bytes)
                logger.debug(
                    "Created instance %d for series %d (StudyUID=%s, SeriesUID=%s, SOPUID=%s)",
                    instance_index + 1, series_index + 1, study_uid, series_uid, sop_uid
                )

            study[f"Series_{series_index + 1}"] = series_instances

        logger.info(
            "Study created with %d series and %d total instances",
            series_count, series_count * instances_per_series
        )

        return study
