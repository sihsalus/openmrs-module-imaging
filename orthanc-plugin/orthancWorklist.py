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
    # Handle new study
    if changeType == orthanc.ChangeType.STABLE_STUDY:
        try:
            studyJson = json.loads(orthanc.RestApiGet("/studies/"+resource))
            studyTags = studyJson["MainDicomTags"]

            studyInfo = {
                "accessionNumber": studyTags.get("AccessionNumber"),
                "studyInstanceUID": studyTags.get("StudyInstanceUID"),
                "referringPhysicianName": studyTags.get("ReferringPhysicianName"),
                "studyDescription": studyTags.get("StudyDescription"),
                "studyID": studyTags.get("StudyID")
            }

            # store all series here
            allSeries = []

            if "Series" in studyJson:
                for seriesID in studyJson["Series"]:

                    seriesJson = json.loads(orthanc.RestApiGet("/series/" + seriesID))
                    seriesTags = seriesJson["MainDicomTags"]

                    orthanc.LogWarning('+++++++++ series json: %s' %
                                       json.dumps(seriesJson, indent = 4))

                    stepID = None
                    instanceInfo = {}

                    # -------------------------------
                    # 1) Try extracting SPS ID at SERIES level
                    # -------------------------------
                    seriesSeq = seriesTags["RequestAttributesSequence"]
                    if seriesSeq and isinstance(seriesSeq, list):
                        for item in seriesSeq:
                            if "ScheduledProcedureStepID" in item:
                                stepID = item["ScheduledProcedureStepID"]
                                break

                    # -------------------------------
                    # 2) If not found, try INSTANCE level
                    # -------------------------------
                    if stepID is None and "Instances" in seriesJson and len(seriesJson["Instances"])>0:
                        instanceJson = json.loads(orthanc.RestApiGet("/instances/" + seriesJson["Instances"][0] + "/tags?simplify"))

                        orthanc.LogWarning('+++++++++ Instances json: %s' %
                                           json.dumps(instanceJson, indent = 4))

                        instSeq = instanceJson["RequestAttributesSequence"]

                        # Find scheduledProcedureStepID
                        if instSeq and isinstance(instSeq, list):
                            for item in instSeq:
                                if "ScheduledProcedureStepID" in item:
                                    stepID = item["ScheduledProcedureStepID"]
                                    break

                        # Extract needed instance-level fields
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

                    orthanc.LogWarning("Step ID of stable series " + seriesID +
                                       "in study " + studyTags.get("StudyInstanceUID") +
                                       ": "+str(stepID))

                    # Extract needed series-level fields
                    seriesInfo = {
                        "seriesID": seriesID,
                        "modality": seriesTags.get("Modality"),
                        "seriesDescription": seriesTags.get("SeriesDescription"),
                        "seriesInstanceUID": seriesTags.get("SeriesInstanceUID"),
                        "stationName": seriesTags.get("StationName"),
                        "parentStudy": studyJson.get("ParentStudy")
                    }

                    # store this series entry
                    allSeries.append({
                        "seriesInfo": seriesInfo,
                        "instanceInfo": instanceInfo,
                        "scheduledProcedureStepID": stepID
                    })

                # -------------------------------
                # 3) If ANY stepID was found → POST payload
                # -------------------------------

                foundIDs = [s["scheduledProcedureStepID"] for s in allSeries if s["scheduledProcedureStepID"]]
                if foundIDs:
                    try:
                        # Final payload with all series info
                        payload = {
                            "studyInfo": studyInfo,
                            "seriesList": allSeries
                        }
                        orthanc.LogWarning("======= Payload sent ======== " + json.dumps(payload, indent=2))

                        # postUrl = updateRequestStatusURL+"?studyInstanceUID=" + studyTags.get("StudyInstanceUID") + "&scheduledProcedureStepID=" + str(stepID)
                        response = requests.post(
                            # postUrl,
                            updateRequestStatusURL,
                            json = payload,
                            auth=(worklistUsername, worklistPassword)
                        )
                        response.raise_for_status()
                    except requests.RequestException as e:
                        orthanc.LogError(f"Failed to update procedure step status: {str(e)}")
        except requests.RequestException as e:
            orthanc.LogError(f"Failed to process stable study: {str(e)}")
    else:
        return None

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


    
        
