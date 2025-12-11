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

import orthanc
import requests
import json


def OnWorkList(answers, query, issuerAet, calledAet):
    # Get query in json format and write it to log
    queryDicom = query.WorklistGetDicomQuery()
    queryJson = json.loads(orthanc.DicomBufferToJson(
        queryDicom, orthanc.DicomToJsonFormat.SHORT, orthanc.DicomToJsonFlags.NONE, 0))
    orthanc.LogWarning('C-FIND worklist request: %s' %
                       json.dumps(queryJson, indent = 4))

    response = requests.get(getWorklistURL, auth=(worklistUsername, worklistPassword))
    responseJson = response.json()

    orthanc.LogWarning('Response by server: %s' % json.dumps(responseJson))

    for dicomJson in responseJson:
        responseDicom = orthanc.CreateDicom(json.dumps(dicomJson), None, orthanc.CreateDicomFlags.NONE)

        orthanc.LogWarning(orthanc.DicomBufferToJson(
            responseDicom, orthanc.DicomToJsonFormat.SHORT, orthanc.DicomToJsonFlags.NONE, 0))

        # Thie code only for test:
        # Save the DICOM buffer to a file
        with open("/tmp/worklist_test.wl", 'wb') as f:
            f.write(responseDicom)

        if query.WorklistIsMatch(responseDicom):
            answers.WorklistAddAnswer(query, responseDicom)

def OnChange(changeType, level, resource):
    if changeType != orthanc.ChangeType.STABLE_STUDY:
        return
    try:
        studyJson = json.loads(orthanc.RestApiGet("/studies/" + resource))

        studyTags = studyJson.get("MainDicomTags", {})
        studyInfo = {
            "accessionNumber": studyTags.get("AccessionNumber"),
            "studyInstanceUID": studyTags.get("StudyInstanceUID"),
            "referringPhysicianName": studyTags.get("ReferringPhysicianName"),
            "studyDescription": studyTags.get("StudyDescription"),
            "studyID": studyTags.get("StudyID")
        }

        allSeries = []
        for seriesID in studyJson.get("Series", []):
            seriesJson = json.loads(orthanc.RestApiGet("/series/" + seriesID))
            orthanc.LogWarning('+++++++++ series json: %s' %
                               json.dumps(seriesJson, indent = 4))
            seriesTags = seriesJson.get("MainDicomTags", {})

            # Reset for each series
            stepID = None
            instanceInfo = {}

            # ---- Extract SPS ID + instance-level fields ----
            if "Instances" in seriesJson and seriesJson["Instances"]:
                instID = seriesJson["Instances"][0]
                instanceJson = json.loads(
                    orthanc.RestApiGet(f"/instances/{instID}/tags?simplify")
                )
                orthanc.LogWarning('+++++++++ Instances json: %s' %
                                   json.dumps(instanceJson, indent = 4))
                instSeq = instanceJson.get("RequestAttributesSequence", [])
                if isinstance(instSeq, list):
                    for item in instSeq:
                        if "ScheduledProcedureStepID" in item:
                            stepID = item["ScheduledProcedureStepID"]
                            break

                instanceInfo = {
                    "patientBirthDate": instanceJson.get("PatientBirthDate"),
                    "patientID": instanceJson.get("PatientID"),
                    "patientName": instanceJson.get("PatientName"),
                    "scheduledProcedureStepID": stepID,
                    "studyInstanceUID": instanceJson.get("StudyInstanceUID"),
                    "numberOfSlices": instanceJson.get("NumberOfSlices"),
                    "scheduledPerformingPhysician": instanceJson.get("PerformingPhysicianName"),
                    "performedProcedureStepDescription": instanceJson.get("PerformedProcedureStepDescription"),
                    "performedProcedureStepStartDate": instanceJson.get("PerformedProcedureStepStartDate"),
                    "performedProcedureStepStartTime": instanceJson.get("PerformedProcedureStepStartTime"),
                    "requestedProcedureDescription": instanceJson.get("RequestedProcedureDescription"),
                }

            # ---- Series-level data ----
            seriesInfo = {
                "seriesID": seriesID,
                "modality": seriesTags.get("Modality"),
                "seriesDescription": seriesTags.get("SeriesDescription"),
                "seriesInstanceUID": seriesTags.get("SeriesInstanceUID"),
                "stationName": seriesTags.get("StationName"),
                "parentStudy": studyJson.get("ParentStudy")
            }

            allSeries.append({
                "seriesInfo": seriesInfo,
                "instanceInfo": instanceInfo,
                "scheduledProcedureStepID": stepID
            })

            if any(s["scheduledProcedureStepID"] for s in allSeries):
                payload = {
                    "studyInfo": studyInfo,
                    "seriesList": allSeries
                }

                orthanc.LogWarning("======= Payload sent ======== " + json.dumps(payload, indent=2))

                response = requests.post(
                    updateRequestStatusURL,
                    json=payload,
                    auth=(worklistUsername, worklistPassword)
                )
                response.raise_for_status()

    except Exception as e:
        orthanc.LogError("Failed to process stable study: " + str(e))


def getConfigItem(configItemName):
    config = orthanc.GetConfiguration()
    configJson = json.loads(config)
    url = configJson[configItemName]
    return url

orthanc.RegisterWorklistCallback(OnWorkList)
orthanc.RegisterOnChangeCallback(OnChange)

# Read the API URL from the configuration of Orthanc
getWorklistURL = getConfigItem("ImagingWorklistURL")
updateRequestStatusURL = getConfigItem("ImagingUpdateRequestStatus")
worklistUsername = getConfigItem("ImagingWorklistUsername")
worklistPassword = getConfigItem("ImagingWorklistPassword")


    
        
