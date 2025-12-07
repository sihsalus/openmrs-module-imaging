<% ui.decorateWith("appui", "standardEmrPage", [ title: ui.message("imaging.app.orthancconfiguration.title") ])
   ui.includeCss("imaging", "general.css");
   ui.includeCss("imaging", "orthancConfiguration.css");
%>

<% ui.includeJavascript("imaging", "sortable.min.js") %>
<% ui.includeJavascript("imaging", "filter_table.js")%>

<h2><img class="orthanc-icon" src="${ ui.resourceLink("imaging", "images/orthanc.png") }"/> ${ ui.message("imaging.app.orthancconfiguration.heading")}</h2>
<br/>
<script>
    function togglePopupAdd() {
        const overlay = document.getElementById('popupOverlayAdd');
        overlay.classList.toggle('show');
    }

    function checkConfiguration() {
        fetch("/${contextPath}/module/imaging/checkConfiguration.form?url="+encodeURI(url.value)+"&proxyurl="+encodeURI(proxyurl.value)+"&username="+username.value+"&password="+password.value)
        .then((response)=> response.text())
        .then((text)=>window.alert(text))
    }

    function togglePopupDeleteOrthancConfiguration(id) {
        const overlay = document.getElementById('popupOverlayDeleteOrthancConfiguration');
        overlay.classList.toggle('show');
        document.deleteOrthancConfigurationForm.action = "/${contextPath}/module/imaging/deleteConfiguration.form?id="+id;
    }

</script>
<div style="color:red;">
${param["message"]?.getAt(0) ?: ""}
</div>
 <% if (privilegeManagerOrthancConfiguration) { %>
    <div>
            <button class="btn-open-popup" onclick="togglePopupAdd()">Add new configuration</button>
    </div>
<% } %>
<div id="table-scroll">
    <table id="imaging-settings" class="table table-sm table-responsive-sm table-responsive-md table-responsive-lg table-responsive-xl" data-sortable>
        <thead class="imaging-table-thead">
           <script src="filter_table.js" defer></script>
           <tr>
                <th>${ ui.message("imaging.app.id.label")}</th>
                <th>${ ui.message("imaging.app.url.label")}</th>
                <th>${ ui.message("imaging.app.proxyUrl.label")}</th>
                <th>${ ui.message("imaging.app.username.label")}</th>
                <th style="width: max-content;">${ ui.message('imaging.delete.action') }</th>
            </tr>
        </thead>
        <tbody>
            <% if (orthancConfigurations.size() == 0) { %>
                <tr>
                    <td colspan="6" class="configure_td">${ui.message("imaging.app.none")}</td>
                </tr>
            <% } %>
            <% orthancConfigurations.each { orthancConfiguration -> %>
                <tr>
                    <td>${ui.format(orthancConfiguration.id)}</td>
                    <td>${ui.format(orthancConfiguration.orthancBaseUrl)}</td>
                    <td>${ui.format(orthancConfiguration.orthancProxyUrl)}</td>
                    <td>${ui.format(orthancConfiguration.orthancUsername)}</td>
                    <td>
                       <% if (privilegeManagerOrthancConfiguration) { %>
                            <a class="delete-configuration"
                               onclick="togglePopupDeleteOrthancConfiguration('${orthancConfiguration.id}')">
                               <img class="delete-img" alt="Show the procedure step" src="${ ui.resourceLink("imaging", "images/delete.png")}"/></a>
                            </a>
                       <% } %>
                    </td>
                </tr>
            <% } %>
        </tbody>
    </table>
</div>
<div id="popupOverlayAdd" class="overlay-container">
    <div class="popup-box">
        <h2 style="color: #009384;">Add Orthanc configuration</h2>
        <form class="form-container" action="/${contextPath}/module/imaging/storeConfiguration.form" method="post">
            <label class="form-label" for="url">${ ui.message("imaging.app.url.label")}</label>
            <input class="form-input" type="text" placeholder="Orthanc URL" id="url" name="url" required>

            <label class="form-label" for="proxyurl">${ ui.message("imaging.app.proxyUrl.label")}</label>
            <input class="form-input" type="text" placeholder="Orthanc Proxy URL" id="proxyurl" name="proxyurl">

            <label class="form-label" for="username">${ ui.message("imaging.app.username.label")}</label>
            <input class="form-input" type="text" placeholder="Orthanc user name" id="username" name="username" required>

            <label class="form-label" for="password">${ ui.message("imaging.app.password.label")}</label>
            <input class="form-input" type="password" placeholder="Orthanc password" id="password" name="password" required>
            <div style="display: flex;">
                <button class="btn-check" type="button" onclick="checkConfiguration()">Check connection</button>
                <button class="btn-submit" type="submit">Save</button>
                <button class="btn-close-popup" type="button" onclick="togglePopupAdd()">Cancel</button>
            </div>
        </form>
    </div>
</div>

<div id="popupOverlayDeleteOrthancConfiguration" class="overlay-container">
    <div class="popup-box" style="width: 65%;">
        <h2>Delete Orthanc Configuration</h2>
        <form name="deleteOrthancConfigurationForm" class="form-container" method='POST'>
            <h3 id="deleteOrthancConfigurationMessage">${ ui.message("imaging.deleteOrthancConfiguration.message") }</h3>
            <div class="popup-box-btn">
                <button class="btn-submit" type="submit">${ ui.message("imaging.action.delete") }</button>
                <button class="btn-close-popup" type="button" onclick="togglePopupDeleteOrthancConfiguration()">Cancel</button>
            </div>
        </form>
    </div>
</div>