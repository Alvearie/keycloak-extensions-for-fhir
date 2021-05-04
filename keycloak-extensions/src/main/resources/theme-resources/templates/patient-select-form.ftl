<#import "template.ftl" as layout>
<@layout.registrationLayout displayInfo=true; section>
    <#if section = "title">
        Patient selection
    <#elseif section = "header">
        Patient selection
    <#elseif section = "form">
        <form action="${url.loginAction}" class="${properties.kcFormClass!}" id="kc-u2f-login-form" method="post">
            <p>Which patient record would you like to access?</p>
            <div>
                <#list patients as patient>
                    <div>
                    <input type="radio" name="patient" id="${patient.id}" value="${patient.id}"/>
                    <label for="${patient.id}">${patient.name} (DOB: ${patient.dob})</label>
                    </div>
                </#list>
            </div>

            <br/>
            <input class="${properties.kcButtonClass!} ${properties.kcButtonPrimaryClass!} ${properties.kcButtonLargeClass!}"
                   type="submit" value="${msg("doSubmit")}"/>

        </form>
    </#if>
</@layout.registrationLayout>