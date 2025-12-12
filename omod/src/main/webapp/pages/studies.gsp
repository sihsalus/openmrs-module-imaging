<%
    ui.decorateWith("appui", "standardEmrPage",  [ title: ui.message("imaging.app.imageStudies.title") ])
    ui.includeCss("imaging", "general.css")
    ui.includeCss("imaging", "studies.css")
%>
<script type="text/javascript">
    var breadcrumbs = [
        { icon: "icon-home", link: '/' + OPENMRS_CONTEXT_PATH + '/index.htm' },
        { label: "${ ui.escapeJs(ui.encodeHtmlContent(ui.format(patient.familyName))) }, ${ ui.escapeJs(ui.encodeHtmlContent(ui.format(patient.givenName))) }",
            link: '${ui.pageLink("coreapps", "clinicianfacing/patient", [patientId: patient.id])}'},
        { label: "${ ui.message("imaging.studies") }" }
    ];
</script>

${ ui.includeFragment("coreapps", "patientHeader", [ patient: patient ]) }
${ ui.includeFragment("uicommons", "infoAndErrorMessage")}
<% ui.includeJavascript("imaging", "sortable.min.js") %>
<% ui.includeJavascript("imaging", "filter_table.js")%>

<h2>
    ${ ui.message("imaging.studies") }y
</h2>

<div style="color:red;">
${param["message"]?.getAt(0) ?: ""}
</div>

<script>
    function togglePopupUpload() {
        const overlay = document.getElementById('popupOverlayUpload');
        overlay.classList.toggle('show');
    }

    function toggleSynchronizeStudies() {
        const overlay = document.getElementById('popupOverlaySynchronization');
        overlay.classList.toggle('show');
    }

    function togglePopupDeleteStudy(studyId, patient) {
        const overlay = document.getElementById('popupOverlayDeleteStudy');
        overlay.classList.toggle('show');
        document.deleteStudyForm.action = "/${contextPath}/module/imaging/deleteStudy.form?studyId="
                                             + studyId
                                             + "&patientId=" + patient;
    }

    function onStudyAutoLinkingChange(studyId, patientId, value) {
        value = parseInt(value)
        if (value === -1) {
            togglePopupAutoUnlinkingStudy(studyId, patientId, value)
        } else {
            togglePopupAutoLinkingStudy(studyId, patientId, value)
        }
    }

    function togglePopupAutoUnlinkingStudy(studyId, patientId, linkStatus) {
        const overlay = document.getElementById('popupOverlayUnlinkingStudy');
        overlay.classList.toggle('show');
        if (studyId && patientId) {
            document.unlinkStudyForm.action = "/${contextPath}/module/imaging/autoLinkStudy.form?patientId="
                                                + patientId
                                                + "&studyId=" + studyId
                                                + "&linkStatus=" + linkStatus
        }
    }

    function togglePopupAutoLinkingStudy(studyId, patientId, linkStatus) {
        const overlay = document.getElementById('popupOverlayLinkingStudy');
        overlay.classList.toggle('show');

        if (!studyId || !patientId) return;

        const tbody = document.getElementById("comparison-result-tbody");
        const scoreSpan = document.getElementById("matching-score");
        if (!tbody || !scoreSpan ) return;

        tbody.innerHTML = "<tr><td colspan='4'>Loading difference data...</td></tr>";

        const url = '/${contextPath}/module/imaging/fetchStudyComparisonResult.form?studyId=' + studyId

        fetch(url, { method: 'GET'})
            .then(response => {
                 if (!response.ok) throw new Error("HTTP error " + response.status);
                 return response.json();
            })
            .then(data => {
                scoreSpan.textContent = data.score != null ? data.score + "%" : "N/A";
                tbody.innerHTML = "";

                if (data.differences && data.differences.length > 0) {
                     data.differences.forEach(diff => {
                        const tr = document.createElement("tr");
                        tr.innerHTML = "<td>" + diff.tag + "</td>" +
                                        "<td>" + diff.fromOpenmrs + "</td>" +
                                        "<td>" + diff.fromPacs + "</td>"
                        tbody.appendChild(tr);
                     });
                } else {
                    tbody.innerHTML = "<tr><td colspan='3'>No difference data available</td></tr>";
                }
            })
            .catch((error) => {
                tbody.innerHTML = "<tr><td colspan='3'>Failed to load difference data: " + error.message + "</td></tr>";
            });

        document.linkingStudyForm.action = "/${contextPath}/module/imaging/autoLinkStudy.form?patientId="
                                                + patientId
                                                + "&studyId=" + studyId
                                                + "&linkStatus=" + linkStatus
    }

</script>

<div>
    <% if (orthancConfigurations.size() == 0) { %>
        No Orthanc server configured
    <% } else { %>
        <% if (privilegeModifyImageData) { %>
            <button class="btn-open-popup-upload" onclick="togglePopupUpload()">Upload Study</button>
        <% } %>
        <button class="btn-open-popup-sync" onclick="toggleSynchronizeStudies()">Get Studies</button>
    <% } %>
</div>

<div id="table-scroll">
    <table id="studies" class="table table-sm table-responsive-sm table-responsive-md table-responsive-lg table-responsive-xl" data-sortable>
        <thead class="imaging-table-thead">
            <script src="filter_table.js" defer></script>
            <tr>
                <th>${ ui.message("imaging.app.studyInstanceUid.label")}</th>
                <th>${ ui.message('imaging.app.studyLink.label')}</th>
                <th>${ ui.message("imaging.app.patientName.label")}</th>
                <th>${ ui.message("imaging.app.date.label")}</th>
                <th>${ ui.message("imaging.app.description.label")}</th>
                <th>${ ui.message("imaging.app.server.label")}</th>
                <th data-no-filter style="width: max-content;">${ ui.message("coreapps.actions") }</th>
            </tr>
        </thead>
        <tbody>
            <% if (studies.size() == 0) { %>
                <tr>
                    <td colspan="6" align="center">${ui.message("imaging.studies.none")}</td>
                </tr>
            <% } %>
            <% studies.each { study ->
                def baseUrl = study.orthancConfiguration.orthancProxyUrl?.trim() ? study.orthancConfiguration.orthancProxyUrl : study.orthancConfiguration.orthancBaseUrl
            %>
                <tr>
                    <td class="uid-td">
                        <a href="${ui.pageLink("imaging", "series", [patientId: patient.id, studyId: study.id])}">${ui.format(study.studyInstanceUID)}</a>
                    </td>
                     <td>
                        <select class="linking-select"
                            onchange="onStudyAutoLinkingChange('${study.id}', '${patient.id}', this.value)">
                            <option value="0" ${study.linkStatus == 0 ? 'selected' : ''}>Manual</option>
                            <option value="1" ${study.linkStatus == 1 ? 'selected' : ''}>Auto. unsure</option>
                            <option value="2" ${study.linkStatus == 2 ? 'selected' : ''}>Auto. 100%</option>
                            <option value="-1" ${study.linkStatus == -1 ? 'selected' : ''}>Unlink</option>
                        </select>
                    </td>
                    <td>${ui.format(study.patientName)}</td>
                    <td>${ui.format(study.studyDate)}</td>
                    <td class="description-td">${ui.format(study.studyDescription)}</td>
                    <td>${ui.format(study.orthancConfiguration.orthancBaseUrl)}</td>
                     <td>
                        <% if (privilegeModifyImageData) { %>
                            <a class="delete-study"
                                onclick="togglePopupDeleteStudy('${study.id}', '${patient.id}')"><i class="icon-remove delete-action"></i>
                            </a>
                        <% } %>
                        <div style="display: flex">
                           <a href="${baseUrl}/stone-webviewer/index.html?study=${ui.format(study.studyInstanceUID)}" title="${ui.message("imaging.app.openStoneView.label") }">
                                <img class="stone-img" alt="Show image in stone viewer" src="${ ui.resourceLink("imaging", "images/stoneViewer.png")}"/></a>
                           <a href="${baseUrl}/ohif/viewer?StudyInstanceUIDs=${ui.format(study.studyInstanceUID)}" title="${ ui.message("imaging.app.openOHIFView.label") }">
                               <img class="ohif-img" alt="Show image in OHIF viewer" src="${ ui.resourceLink("imaging", "images/ohifViewer.png")}"/></a>
                           <a href="${baseUrl}/ui/app/#/filtered-studies?StudyInstanceUID=${ui.format(study.studyInstanceUID)}&expand=study" title="${ ui.message("imaging.app.orthancExplorer.label") }">
                               <img class="orthanc-img" alt="Show image data in Orthanc explorer" src="${ ui.resourceLink("imaging", "images/orthanc.png")}"/></a>
                       </div>
                    </td>
                </tr>
            <% } %>
        </tbody>
    </table>
</div>
<div id="popupOverlayUpload" class="overlay-container">
    <div class="popup-box">
        <h2 style="color: #009384;">Upload study</h2>
        <form class="form-container" enctype='multipart/form-data' method='POST' action='/${contextPath}/module/imaging/uploadStudy.form?patientId=${patient.id}'>
            <label class="form-label" for="server">Select Orthanc server</label>
            <select class="select-config" id="orthancConfigurationId" name="orthancConfigurationId">
                <% orthancConfigurations.each { config -> %>
                    <option value="${config.id}">${ui.format(config.orthancBaseUrl)}</option>
                <% } %>
            </select>
            <label class="form-label" for="files" style="color: red;">Select files to upload (dicom files or zip files containing dicom files. Note: You cannot upload more than ${ui.format(maxUploadImageDataSize)} MB in one go!)</label>
            <input class="form-input" type='file' name='files' multiple>
            <div class="popup-box-btn">
                <button class="btn-submit" type="submit">Upload</button>
                <button class="btn-close-popup" type="button" onclick="togglePopupUpload()">Cancel</button>
            </div>
        </form>
    </div>
</div>

<div id="popupOverlaySynchronization" class="overlay-container">
    <div class="popup-box">
        <h2 style="color: #009384;">Fetch studies from providers</h2>
        <form class="form-container" method='POST' action='/${contextPath}/module/imaging/syncStudies.form?patientId=${patient.id}'>
            <div class="radio-div">
                <label style="margin-right: 30px; color: #5B57A6;">
                    <input style="width:20px; margin-top: 2px; height: 20px;" type="radio" id="fetchAll" name="fetchOption" value="all" checked>Get all studies</label>
                <label style="margin-left: 30px; color: #5B57A6;">
                    <input style="width:20px; margin-top: 2px; height: 20px;" type="radio" id="fetchNewest" name="fetchOption" value="newest">Get the latest studies</label>
            </div>
            <label class="form-label" for="server">Select Orthanc server</label>
            <select class="select-config" id="orthancConfigurationId" name="orthancConfigurationId">
                <option value="-1">All servers</option>
                <% orthancConfigurations.each { config -> %>
                    <option value="${config.id}">${ui.format(config.orthancBaseUrl)}</option>
                <% } %>
            </select>
            <div class="popup-box-btn">
                <button class="btn-submit" type="submit">Start</button>
                <button class="btn-close-popup" type="button" onclick="toggleSynchronizeStudies()">Cancel</button>
            </div>
        </form>
    </div>
</div>

<div id="popupOverlayDeleteStudy" class="overlay-container">
    <div class="popup-box" style="width: 65%;">
        <h2>Delete study</h2>
        <form name="deleteStudyForm" class="form-container" method='POST'>
            <h2 id="deleteStudyMessage">${ ui.message("imaging.deleteStudy.message") }</h3>
            <div class="radio-div">
                <label style="margin-right: 30px; color: #5B57A6">
                    <input style="width:20px; margin-top: 2px; height: 20px;" type="radio" id="deleteOpenmrs" name="deleteOption" value="openmrs" checked>Delete from OpenMRS</label>
                <label style="margin-left: 30px; color: #5B57A6;">
                    <input style="width:20px; margin-top: 2px; height: 20px;" type="radio" id="deleteBoth" name="deleteOption" value="openmrsOrthanc">Delete from OpenMRS and Orthanc</label>
            </div>
            <div class="popup-box-btn" style="margin-top: 40px;">
                <button class="btn-submit" type="submit">${ ui.message("imaging.action.delete") }</button>
                <button class="btn-close-popup" type="button" onclick="togglePopupDeleteStudy()">Cancel</button>
            </div>
        </form>
    </div>
</div>

<div id="popupOverlayUnlinkingStudy" class="overlay-container">
    <div class="popup-box" style="width: 65%">
        <h2>Unlink image study</h2>
        <form name="unlinkStudyForm" class="form-container" method='POST'>
            <h2 id="unlinkStudyMessage">${ ui.message("imaging.unlinkStudy.message") }</h2>
            <div class="popup-box-btn" style="margin-top: 40px;">
                <button class="btn-submit" type="submit">Submit</button>
                <button class="btn-close-popup" type="button" onclick="togglePopupAutoUnlinkingStudy()">Cancel</button>
            </div>
        </form>
    </div>
</div>

<div id="popupOverlayLinkingStudy" class="overlay-container">
    <div class="popup-box" style="width: 65%">
        <h2>Unlink image study</h2>
        <form name="linkingStudyForm" class="form-container" method='POST'>
            <h3>Calculated matching score:<span id="matching-score">xx%</span></h3>
            <h3 style="margin-top: 10px; margin-bottom: 10px;">Difference:</h3>
            <table class="table comparisonTable no-filter">
                <thead>
                    <tr>
                        <th>Tag name</th>
                        <th>From OpenMRS</th>
                        <th>From Orthanc</th>
                    </tr>
                </thead>
                <tbody id="comparison-result-tbody"
                    <tr>
                        <td colspan="3">Loading...</td>
                    </tr>
                </tbody>
            </table>
            <div class="popup-box-btn" style="margin-top: 40px;">
                <button class="btn-submit" type="submit">Submit</button>
                <button class="btn-close-popup" type="button" onclick="togglePopupAutoLinkingStudy()">Cancel</button>
            </div>
        </form>
    </div>
</div>















