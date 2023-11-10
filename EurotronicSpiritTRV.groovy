/*
 * Eurotronic Spirit TRV driver for Hubitat 
 *
 * Description:
 * 
 *
 * Information:
 * https://community.hubitat.com/t/eurotronic-air-quality-and-z-wave-spirit-in-association/79841
 *
 * Credits:  
 *
 * Licensing:
 * Copyright 2020-2021 Ravil Rubashkin
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at: http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under the License.
 *
 * Version Control:
 * 1.0.0  2021-02-06 aelfot    Initial version
 * 1.1.0  2021-09-20 aelfot    latest aelfot version on GitHub
 * 2.0.0  2021-12-17 kkossev   English language option and translation;
 * 2.0.1  2021-12-17 kkossev   Added Refresh and Initialize; added forceStateChange option
 * 2.0.2  2021-12-17 kkossev   Added refreshRate
 * 2.0.3  2021-12-18 kkossev   Added calibrate function
 * 2.0.4  2021-12-18 kkossev   calibrate optimization
 * 2.0.5  2022-12-16 kkossev   SwitchLevel capability removed
 * 2.1.0  2023-11-10 kkossev   (dev. branch) VSC; merged latest aelfor changes; improved logDebug; added level attribute; calibrate retries bug fix; added refresh 30 and 60 minutes; made the polling after temperature change configurable;
 *                             added calibrate as a mode; added logsOff; removed Initialize as capability; implemented health check
 *
 *                    TODO: add driver vesion to the states; 
 *                    TODO: add lastRunnimngMode ?
 *                    TODO: do not start calibrate if the valve is fully opened or closed ?
 *                    TODO: implement ping
 *
*/

def version() { "2.1.0" }
def timeStamp() {"2023/11/10 11:32 AM"}

import groovy.transform.Field
import hubitat.helper.HexUtils
import hubitat.device.HubAction

metadata {
    definition (name: "Eurotronic Spirit TRV", namespace: "aelfot", author: "Ravil Rubashkin", importUrl: "https://raw.githubusercontent.com/kkossev/hubitat-Aelfot-fork/development/EurotronicSpiritTRV.groovy", singleThreaded: true ) {
        capability "Battery"
        capability "Thermostat"
        capability "Actuator"
        capability "Sensor"
        capability "TemperatureMeasurement"
        capability "Polling"
        capability "Refresh"
        //capability "Initialize"
        capability "HealthCheck"
        
        attribute "externeTemperatur",  "string"
        attribute "Notifity",           "string"
        attribute "deviceResetLocally", "bool"
        attribute "level",              "number" // valve position
        attribute "lock", "enum", ["locked", "unlocked with timeout", "unlocked", "unknown"]

        //command "SendTemperature", [[name: "Temperature", type: "NUMBER", description:""]]
        command "initialize"
        command "manual"
        command "disableLocalOperations"    //"lokaleBedinungDeaktiviert"
        command "calibrate"
        command "lock"
        command "unlock"

        fingerprint mfr:"0148", prod:"0003", deviceId:"0001", inClusters:"0x5E,0x55,0x98,0x9F"
        fingerprint mfr:"0148", prod:"0003", deviceId:"0001", inClusters:"0x5E,0x85,0x59,0x86,0x72,0x5A,0x75,0x31,0x26,0x40,0x43,0x80,0x70,0x71,0x73,0x98,0x9F,0x55,0x6C,0x7A"
    }
    
    def batteriestatus =  [:]
        batteriestatus << [0 : englishLang==true ? "Event-driven" : "Eventgesteuert"]
        batteriestatus << [1 : englishLang==true ? "Once per day" : "1 Mal täglich"]

    def windowDetectOptions =  [:]
        windowDetectOptions << [0 : englishLang==true ? "Deactivated" : "Deaktiviert"]
        windowDetectOptions << [1 : englishLang==true ? "Low sensitivity" : "Empfindlichkeit niedrig"]
        windowDetectOptions << [2 : englishLang==true ? "Medium sensitivity" : "Empfindlichkeit mittel"]
        windowDetectOptions << [3 : englishLang==true ? "High sensitivity" : "Empfindlichkeit hoch"]

    def refreshRates = [:]
        refreshRates << ["0" : englishLang==true ? "Disabled - Set temperature, valve & battery reports, if required" : "Deaktiviert – Temperatur, Valve und Batterieberichte einstellen, falls erforderlich"]
        refreshRates << ["1" : englishLang==true ? "Refresh every minute (Not recommended)" : "Jede Minute aktualisieren (Nicht empfohlen)"]
        refreshRates << ["5" : englishLang==true ? "Refresh every 5 minutes" : "Alle 5 Minuten aktualisieren"]
        refreshRates << ["10" : englishLang==true ? "Refresh every 10 minutes" : "Alle 10 Minuten aktualisieren"]
        refreshRates << ["15" : englishLang==true ? "Refresh every 15 minutes" : "Alle 15 Minuten aktualisieren"]
        refreshRates << ["30" : englishLang==true ? "Refresh every 30 minutes" : "Alle 30 Minuten aktualisieren"]
        refreshRates << ["60" : englishLang==true ? "Refresh every 60 minutes" : "Alle 60 Minuten aktualisieren"]

    preferences {
        input (name: "txtEnable", type: "bool",   title: "<b>Description text logging</b>", description: "<i>Display sensor states on HE log page. The recommended value is <b>true</b></i>", defaultValue: true)
        input (name: "logEnable", type: "bool",   title: "<b>Debug logging</b>", description: "<i>Debug information, useful for troubleshooting. The recommended value is <b>false</b></i>", defaultValue: true)
        input name:"englishLang",        type:"bool",    title: "<b>English Language</b>",                  description: "Default: Yes",                              defaultValue:true
        if (englishLang==true) {
            input name: "parameter1",    type:"bool",    title: "<b>Invert LCD</b>",                        description: "Default: No",                               defaultValue:false
            input name: "parameter2",    type:"number",  title: "<b>LCD Timeout (in secs)</b>",             description: "Default: 0-Always on, range 0..30",         defaultValue:0,      range: "0..30"
            input name: "parameter3",    type:"bool",    title: "<b>Backlight</b>",                         description: "Default: Deactivated",                      defaultValue:false
            input name: "parameter4",    type:"enum",    title: "<b>Battery reporting</b>",                 description: "Default: once a day",                       defaultValue:1,      options: batteriestatus
            input name: "parameter5",    type:"number",  title: "<b>Temperature reporting</b>",             description: "Default: 0.5° change, range 0.0..5.0",      defaultValue:0.5,    range: "0.0..5.0"
            input name: "parameter6",    type:"number",  title: "<b>Valve reporting on % change</b>",       description: "Default: 1%, range: 0..100",                defaultValue:1,      range: "0..100"
            input name: "parameter7",    type:"enum",    title: "<b>Window Open Detection</b>",             description: "Default: Medium sensitivity",               defaultValue:2,      options: windowDetectOptions
            input name: "parameter8",    type:"number",  title: "<b>Temperature offset</b>",                description: "Default: no correction. range: -5.0..5.0",  defaultValue:0,      range: "-5.0..5.0"
            input name: "parameter9",    type:"bool",    title: "<b>Use external temperature sensor?</b>",  description: "Default: No",                               defaultValue:false
            input name: "forceStateChange",type:"bool",  title: "<b>Force State Change</b>",                description: "Default: No (used for better graphs only)", defaultValue:false
            input name: "forcePolling",  type:"bool",    title: "<b>Force TRV polling after changes</b>",   description: "Default: No (used for faster status update)", defaultValue:false
            input name: "refreshRate",   type: "enum",   title: "<b>Refresh rate</b>",                      description: "Select refresh rate",                       defaultValue: "0",   required: false, options: refreshRates
        }
        else
        {
            input name: "parameter1",    type:"bool",    title: "<b>Display invertieren?</b>",              description: "Default: Nein",                   defaultValue:false
            input name: "parameter2",    type:"number",  title: "<b>Display ausschalten nach</b>",          description: "Default: Immer an(0)",            defaultValue:0,      range: "0..30"
            input name: "parameter3",    type:"bool",    title: "<b>Hintergrundbeleuchtung</b>",            description: "Default: Deaktiviert",            defaultValue:false
            input name: "parameter4",    type:"enum",    title: "<b>Batteryabfrage</b>",                    description: "Default: 1 mal täglich",          defaultValue:1,      options: batteriestatus
            input name: "parameter5",    type:"number",  title: "<b>Meldung bei Temperaturdifferenz</b>",   description: "Default: bei Delta 0.5°",         defaultValue:0.5,    range: "0.0..5.0"
            input name: "parameter6",    type:"number",  title: "<b>Meldung bei Valvedifferenz</b>",        description: "Default: Deaktiviert",            defaultValue:1,      range: "0..100"
            input name: "parameter7",    type:"enum",    title: "<b>Fensteroffnungserkennung</b>",          description: "Default: Empfindlichkeit mittel", defaultValue:2,      options: windowDetectOptions
            input name: "parameter8",    type:"number",  title: "<b>Temperature offset</b>",                description: "Default: Keine Korrektur",        defaultValue:0,      range: "-5.0..5.0"
            input name: "parameter9",    type:"bool",    title: "<b>Temperatur extern bereitgestellt?</b>", description: "Default: Nein",                   defaultValue:false
            input name: "forceStateChange",type:"bool",  title: "<b>Force State Change</b>",                description: "Default: Nein (nur für bessere Grafiken verwendet)",   defaultValue:false
            input name: "forcePolling",  type:"bool",    title: "<b>TRV-Abfrage nach Änderungen erzwingen</b>",   description: "Default: Nein (wird für eine schnellere Statusaktualisierung verwendet)", defaultValue:false
            input name: "refreshRate",   type: "enum",   title: "<b>Aktualisierungsrate</b>",               description: "Default: Nein",                   defaultValue: "0",   required: false, options: refreshRates
        }            
    }
}

@Field static Map commandClassVersions = [
     0x85:2,    //Association
     0x59:1,    //Association Group Information
     0x20:1,    //Basic
     0x80:1,    //Battery
     0x70:1,    //Configuration
     0x5A:1,    //Device Reset Locally
     0x7A:3,    //Firmware Update Md V3
     0x72:1,    //Manufacturer Specific
     0x31:5,    //Multilevel Sensor
     0x26:1,    //Multilevel Switch
     0x71:8,    //Notifikation
     0x73:1,    //Power Level
     0x75:1,    //Protection
     0x98:2,    //Security ohne verschlüsselung
     0x40:3,    //Thermostat Mode
     0x43:3,    //Thermostat Setpoint
     0x55:2,    //Transport Service ohne verschlüsselung
     0x86:2,    //Version
     0x5E:2     //Z-Wave Plus Info ohne verschlüsselung
] 

@Field static final Integer pollTimer = 3    // seconds
@Field static final Integer presenceCountTreshold = 4

def parse(String description) {
    checkDriverVersion()
    setPresent()
    def cmd = zwave.parse(description, commandClassVersions)
    if (cmd) {
        return zwaveEvent(cmd)
    } else {
        log.debug "Non-parsed event: ${description}"
    }
}

void zwaveEvent (hubitat.zwave.commands.notificationv8.NotificationReport cmd) {
    logDebug "received NotificationReport: ${cmd}"
    def resultat = [:]
    resultat.name = "Notifity"
    resultat.displayed = true
    switch (cmd.notificationType) {
        case 0x08:
            if (cmd.event == 0x0A) {
                resultat.value = englishLang==true ? "Less than 25% battery remaining" : "25% Batterie verbleibend"
            } else if (cmd.event == 0x0B) {
                resultat.value = englishLang==true ? "Less than 15% battery remaining" : "15% Batterie verbleibend"
            } else {
                resultat.value = englishLang==true ? "battery was changed" : "Batterie gewechselt"
            }
            break;
        case 0x09:
            if (cmd.event == 0x03) {
                if (cmd.eventParametersLength != 0) {
                    switch (cmd.eventParameter[0]) {
                        case 0x01:
                            resultat.value = englishLang==true ? "Motor movement not possible" : "Kein Schließpunkt gefunden"
                            break;
                        case 0x02:
                            resultat.value = englishLang==true ? "Not mounted on a valve" : "Keine Ventilbewegung möglich"
                            break;
                        case 0x03:
                            resultat.value = englishLang==true ? "Valve closing point could not be detected" : "Kein Ventilschließpunkt gefunden"
                            break;
                        case 0x04:
                            resultat.value = englishLang==true ? "Piston positioning failed" : "Positionierung fehlgeschlagen"
                            break;
                    }
                } else {
                    resultat.value = englishLang==true ? "Valve problem was fixed" : "Der Fehler wurde gerade behoben"
                }
            }
            break;
    }
    logInfo englishLang ? "Notifikaiton is ${resultat.value}" : "Notifikaiton ist ${resultat.value}"
    if (forceStateChange==true) {resultat.isStateChange = true}
    sendEvent(resultat)
}

void zwaveEvent (hubitat.zwave.commands.thermostatsetpointv3.ThermostatSetpointReport cmd) {
    logDebug "received ThermostatSetpointReport: ${cmd}"
    def resultat = [:]
    resultat.value = cmd.scaledValue
    resultat.unit = getTemperatureScale()
    resultat.displayed = true
    if (cmd.setpointType == 0x01) {
        resultat.name = "heatingSetpoint"
    }
    if (cmd.setpointType == 0x0B) {
        resultat.name = "coolingSetpoint"
    }
    resultat.unit = cmd.scale == 1 ? "°F" : "°C"
    logInfo englishLang ? "Thermostat report: ${resultat.name} is ${resultat.value} ${resultat.unit}" : "Thermostat hat den ${resultat.name} Report ${resultat.value} ${resultat.unit}"
    if (forceStateChange==true) {resultat.isStateChange = true}
    sendEvent(resultat)
}

void zwaveEvent (hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
    logInfo englishLang ? "Battery report ${cmd.batteryLevel} %" : "batteryreport ist ${cmd.batteryLevel} %"
    sendEvent(name:"battery", value: cmd.batteryLevel, unit: "%", displayed: true, isStateChange: true)
}

void zwaveEvent(hubitat.zwave.commands.deviceresetlocallyv1.DeviceResetLocallyNotification cmd) {
    logWarn englishLang ? "Device Reset Locally" : "Device Reset Locally"
    sendEvent(name:"deviceResetLocally", value: true, displayed = true, isStateChange: true)
    sendEvent(name:"Notifity", value:"Deleted")
    sendEvent(name:"thermostatMode", value:"off")
    sendEvent(name:"level", value:0)
    sendEvent(name:"temperature", value:0)
    sendEvent(name:"thermostatOperatingState", value: "idle")
    sendEvent(name:"coolingSetpoint", value:0)
    sendEvent(name:"heatingSetpoint", value:0)
}

void zwaveEvent (hubitat.zwave.commands.protectionv1.ProtectionReport cmd) {
    logDebug "received ProtectionReport: ${cmd}"
    def resultat = [:]
    resultat.name = "lock"
    resultat.displayed = true
    switch (cmd.protectionState) {
        case 0:
            resultat.value = "unlocked"
            break;
        case 1:
            resultat.value = "locked"
            break;
        case 2:
            resultat.value = englishLang==true ? "No local operation possible" : "lokale Bedinung deaktiviert"
            break;
    }
    if (forceStateChange==true) {resultat.isStateChange = true}
    if (resultat.value != null) {sendEvent(resultat)}
    logInfo englishLang ? "protection report is ${resultat.value}" :"protection report ist ${resultat.value}"
}

void zwaveEvent (hubitat.zwave.commands.thermostatmodev3.ThermostatModeReport cmd) {
    logDebug "received ThermostatModeReport: ${cmd}"
    def resultat = [:]
    resultat.name = "thermostatMode"
    resultat.displayed = true
    switch (cmd.mode) {
        case 0:
            resultat.value = "off"
            break;
        case 1:
            resultat.value = "heat"
            break;
        case 11:
            resultat.value = "cool"
            break;
        case 15:
            resultat.value = "emergency heat"
            break;
        case 31:
            resultat.value = "manual"
            break;
        default :
            log.warn "Thermostat reported unknown mode ${cmd.mode}"
    }
    if (forceStateChange==true) {resultat.isStateChange = true}
    sendEvent(resultat)
    if (true /*parameter6 == 0*/) { 
        sendToDevice (new hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelGet(sensorType:1))         // temperature
        sendToDevice (new hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelGet())                    // valve position
    }
    logInfo englishLang ? "Thermostat reported mode is ${resultat.value}" : "thermostat hat den mode gemeldet ${resultat.value}"
}

void zwaveEvent (hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {
    logDebug "received SensorMultilevelReport: ${cmd}"
    def resultat = [:]
    resultat.value = cmd.scaledSensorValue
    // round to one decimal place
    resultat.value = Math.round(resultat.value * 10) / 10
    resultat.name = "temperature"
    resultat.unit = cmd.scale == 1 ? "°F" : "°C"
    resultat.displayed = true
    logInfo englishLang ? "temperature is ${resultat.value} ${resultat.unit}" : "temperature ist ${resultat.value} ${resultat.unit}"
    if (parameter6 == 0) {
        def operatingstate = [:]
        operatingstate.name = "thermostatOperatingState"
        operatingstate.displayed = true
        switch (device.currentValue("thermostatMode")) {
            case "off":
                operatingstate.value = "idle"
                break;
            case "heat":            
                if (cmd.scaledSensorValue < device.currentValue("heatingSetpoint").toFloat()) {
                    operatingstate.value = "heating"
                } else if (cmd.scaledSensorValue > device.currentValue("heatingSetpoint").toFloat()) {
                    operatingstate.value = "cooling"
                } else {
                    operatingstate.value = "idle"
                }
                break;
            case "cool":                
                if (cmd.scaledSensorValue < device.currentValue("coolingSetpoint").toFloat()) {
                    operatingstate.value = "heating"
                } else if (cmd.scaledSensorValue > device.currentValue("coolingSetpoint").toFloat()) {
                    operatingstate.value = "cooling"
                } else {
                    operatingstate.value = "idle"
                }
                break;
            case "emergency heat":
                operatingstate.value = "pending heat"
                break;
            case "manual":
                operatingstate.value = "vent economizer"
                break;
        }
        sendEvent(operatingstate)
    }
    sendEvent(resultat)    
}

void thermostatLevelAndOperatingStateEvents (valvePos)
{
    logDebug "received thermostatLevelAndOperatingStateEvents: ${valvePos}"
    def valvePosition = valvePos
    def resultat = [:]
    resultat.name = "thermostatOperatingState"
    resultat.displayed = true
    if (valvePosition == 0) {
        resultat.value = "idle"
    } else if (valvePosition < 10) {
        resultat.value = "cooling"
    } else {
        resultat.value = "heating"
    }
    logInfo englishLang ? "Valve position is ${valvePosition} %" : "Valveposition ist ${valvePosition} %"
    logInfo englishLang ? "Operating state is ${resultat.value}" : "Operating state ist ${resultat.value}"
    if (forceStateChange==true) {resultat.isStateChange = true}
    sendEvent(name:"level", value: valvePosition, isStateChange: true, unit: "%")
    sendEvent(resultat)    
    
}

void zwaveEvent (hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelReport cmd) {
    logDebug "received SwitchMultilevelReport: ${cmd}"
    thermostatLevelAndOperatingStateEvents(cmd.value)
}

void zwaveEvent(hubitat.zwave.Command cmd) {
    logDebug "received Unhandled event ${cmd}"
    log.debug englishLang==true ? "${device.displayName}: Unhandled: $cmd" : "${device.displayName}: Unhandled: $cmd"
}

void off() {
    logDebug "off"
    def cmds = []
    cmds << new hubitat.zwave.commands.thermostatmodev3.ThermostatModeSet(mode:0x00)
    cmds << new hubitat.zwave.commands.thermostatmodev3.ThermostatModeGet()
    sendToDevice(cmds)
    sendEvent(name:"thermostatOperatingState", value: "idle", isStateChange: true)
}

void heat() {
    logDebug "heat"
    def cmds = []
    cmds << new hubitat.zwave.commands.thermostatmodev3.ThermostatModeSet(mode:0x01)
    cmds << new hubitat.zwave.commands.thermostatmodev3.ThermostatModeGet()
    cmds << new hubitat.zwave.commands.thermostatsetpointv3.ThermostatSetpointGet(setpointType:0x01)
    sendToDevice(cmds)
}

void cool() {
    logDebug "cool"
    def cmds = []
    cmds << new hubitat.zwave.commands.thermostatmodev3.ThermostatModeSet(mode:0x0B)
    cmds << new hubitat.zwave.commands.thermostatmodev3.ThermostatModeGet()
    cmds << new hubitat.zwave.commands.thermostatsetpointv3.ThermostatSetpointGet(setpointType:0x0B)
    sendToDevice(cmds)
}

void emergencyHeat() {
    logDebug "emergencyHeat"
    def cmds = []
    cmds << new hubitat.zwave.commands.thermostatmodev3.ThermostatModeSet(mode:0x0F)
    cmds << new hubitat.zwave.commands.thermostatmodev3.ThermostatModeGet()
    sendToDevice(cmds)
    sendEvent(name:"thermostatOperatingState", value: "heating", isStateChange: true)
}

void manual() {
    logDebug "manual"
    def cmds = []
    cmds << new hubitat.zwave.commands.thermostatmodev3.ThermostatModeSet(mode:0x1F)
    cmds << new hubitat.zwave.commands.thermostatmodev3.ThermostatModeGet()
    cmds << new hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelSet(value: 0)
    cmds << new hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelGet()
    sendToDevice(cmds)
}

void zwaveEvent (hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
    logDebug "received ConfigurationReport: ${cmd}"
    def cmds = []
    switch (cmd.parameterNumber) {
        case 1:
            if ((parameter1 ? 0x01 : 0x00) != cmd.scaledConfigurationValue) {
                logInfo englishLang ?"Parameter number ${cmd.parameterNumber} was not set, trying again" : "Parameter nummer ${cmd.parameterNumber} hat den Wert nich übernommen, erneter Versuch"
                cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:1,    size:1,    scaledConfigurationValue: parameter1 ? 0x01 : 0x00)
            } else {
                logInfo englishLang ?"Parameter number  ${cmd.parameterNumber} was successfuly set" : "Parameter nummer  ${cmd.parameterNumber} hat den Wert erfolgreich übernommen"
            }
            break;
        case 2:
            if (Math.round(parameter2).toInteger() != cmd.scaledConfigurationValue) {
                logInfo englishLang ? "Parameter number ${cmd.parameterNumber} was not set, trying again" : "Parameter nummer ${cmd.parameterNumber} hat den Wert nich übernommen, erneter Versuch"
                cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:2,    size:1,    scaledConfigurationValue: Math.round(parameter2).toInteger())
            } else {
                logInfo englishLang ? "Parameter number  ${cmd.parameterNumber} was successfuly set" : "Parameter nummer  ${cmd.parameterNumber} hat den Wert erfolgreich übernommen"
                state.parameter2 = parameter2
            }
            break;
        case 3:
            if ((parameter3 ? 0x01 : 0x00) != cmd.scaledConfigurationValue) {
                logInfo englishLang ? "Parameter number ${cmd.parameterNumber} was not set, trying again" : "Parameter nummer ${cmd.parameterNumber} hat den Wert nich übernommen, erneter Versuch"
                cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:3,    size:1,    scaledConfigurationValue: parameter3 ? 0x01 : 0x00)
            } else {
                logInfo englishLang ? "Parameter number  ${cmd.parameterNumber} was successfuly set" : "Parameter nummer  ${cmd.parameterNumber} hat den Wert erfolgreich übernommen"
                state.parameter3 = parameter3
            }
            break;
        case 4:
            if (parameter4.toInteger() != cmd.scaledConfigurationValue) {
                logInfo englishLang ? "Parameter number ${cmd.parameterNumber} was not set, trying again" : "Parameter nummer ${cmd.parameterNumber} hat den Wert nich übernommen, erneter Versuch"
                cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:4,    size:1,    scaledConfigurationValue: parameter4.toInteger())
            } else {
                logInfo englishLang ? "Parameter number  ${cmd.parameterNumber} was successfuly set" : "Parameter nummer  ${cmd.parameterNumber} hat den Wert erfolgreich übernommen"
                state.parameter4 = parameter4                
            }
            break;
        case 5:
            if (Math.round(parameter5.toFloat() * 10).toInteger() != cmd.scaledConfigurationValue) {
                logInfo englishLang ? "Parameter number ${cmd.parameterNumber} was not set, trying again" : "Parameter nummer ${cmd.parameterNumber} hat den Wert nich übernommen, erneter Versuch"
                cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:5,    size:1,    scaledConfigurationValue: Math.round(parameter5.toFloat() * 10).toInteger())
            } else {
                logInfo englishLang ? "Parameter number  ${cmd.parameterNumber} was successfuly set" : "Parameter nummer  ${cmd.parameterNumber} hat den Wert erfolgreich übernommen"
                state.parameter5 = parameter5
            }
            break;
        case 6:
            if (Math.round(parameter6).toInteger() != cmd.scaledConfigurationValue) {
                logInfo englishLang ? "Parameter number ${cmd.parameterNumber} was not set, trying again" : "Parameter nummer ${cmd.parameterNumber} hat den Wert nich übernommen, erneter Versuch"
                cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:6,    size:1,    scaledConfigurationValue: Math.round(parameter6).toInteger())
            } else {
                logInfo englishLang ? "Parameter number  ${cmd.parameterNumber} was successfuly set" : "Parameter nummer  ${cmd.parameterNumber} hat den Wert erfolgreich übernommen"
                state.parameter6 = parameter6
            }
            break;
        case 7:
            if (parameter7.toInteger() != cmd.scaledConfigurationValue) {
                logInfo englishLang ? "Parameter number ${cmd.parameterNumber} was not set, trying again" : "Parameter nummer ${cmd.parameterNumber} hat den Wert nich übernommen, erneter Versuch"
                cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:7,    size:1,    scaledConfigurationValue: parameter7.toInteger())
            } else {
                logInfo englishLang ? "Parameter number  ${cmd.parameterNumber} was successfuly set" : "Parameter nummer  ${cmd.parameterNumber} hat den Wert erfolgreich übernommen"
                state.parameter7 = parameter7
            }
            break;
        case 8:
            if (parameter9) {
                if (cmd.scaledConfigurationValue != -128) {
                    logInfo englishLang ? "Parameter number 9 was not set, trying again" : "Parameter nummer 9 hat den Wert nich übernommen, erneter Versuch"
                    cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:8,    size:1,    scaledConfigurationValue: -128)
                } else {
                    logInfo englishLang ? "Parameter number 8 was successfuly set" : "Parameter nummer 8 hat den Wert erfolgreich übernommen"
                    logInfo englishLang ? "Parameter number 9 was successfuly set" : "Parameter nummer 9 hat den Wert erfolgreich übernommen"
                    state.parameter8 = parameter8
                    state.parameter9 = parameter9
                    sendEvent (name: "externeTemperatur", value: "true")
                }
            } else  {
                if (cmd.scaledConfigurationValue != Math.round(parameter8.toFloat() * 10).toInteger()) {
                    logInfo englishLang ? "Parameter number ${cmd.parameterNumber} was not set, trying again" : "Parameter nummer ${cmd.parameterNumber} hat den Wert nich übernommen, erneter Versuch"
                    cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:8,    size:1,    scaledConfigurationValue: Math.round(parameter8.toFloat() * 10).toInteger())
                } else {
                    logInfo englishLang ? "Parameter number 8 was successfuly set" : "Parameter nummer 8 hat den Wert erfolgreich übernommen"
                    logInfo englishLang ? "Parameter number 9 was successfuly set" : "Parameter nummer 9 hat den Wert erfolgreich übernommen"
                    state.parameter8 = parameter8
                    state.parameter9 = parameter9                    
                    sendEvent (name: "externeTemperatur", value: "false")
                }
            }
            break;
    }
    if (cmds != []) {
        cmds << new hubitat.zwave.commands.configurationv1.ConfigurationGet(parameterNumber:cmd.parameterNumber)
        sendToDevice(cmds)
    }
}

void setLevel(nextLevel) {
    logDebug "setLevel: ${nextLevel}"
    def val = 0
    try {
        val = nextLevel.toInteger()
    } catch (e) {
        val = 0
    }
    if (val < 0) {val = 0}
    if (val > 100) {val = 100}
    if (device.currentValue("thermostatMode") == "manual") {
        def cmds = []
        cmds << new hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelSet(value: val)
        cmds << new hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelGet()
        sendToDevice(cmds)
        logInfo englishLang ? "Valve was set to ${val} %" : "Die Valve wird auf den Wert ${val} gestellt"
    } else {
        sendToDevice(new hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelGet())
        logInfo englishLang ? "Valve opening position can only be set in manual mode" : "Eine Einstellung der Valveöffnung ist nur im manual modus möglich"
    }
}

void setCoolingSetpoint(temperature) {
    logDebug "setCoolingSetpoint: ${temperature}"
    def nextTemperature = getTemperature (temperature,"cool")
    sendEvent(name: "coolingSetpoint", value: nextTemperature.toFloat(), displayed: true,  unit: "°" + getTemperatureScale())
    def cmds = []
    cmds << new hubitat.zwave.commands.thermostatsetpointv3.ThermostatSetpointSet(precision:1, scale:0, scaledValue: nextTemperature, setpointType: 0x0B)
    cmds << new hubitat.zwave.commands.thermostatsetpointv3.ThermostatSetpointGet(setpointType:0x0B)
    sendToDevice(cmds)
    if (forcePolling==true) {runIn(pollTimer, "poll", [overwrite: true])}
}

void setHeatingSetpoint(temperature) {
    logDebug "setHeatingSetpoint: ${temperature}"
    def nextTemperature = getTemperature (temperature,"heat")
    sendEvent(name: "heatingSetpoint", value: nextTemperature.toFloat(), displayed: true, unit: "°" + getTemperatureScale())
    def cmds = []
    cmds << new hubitat.zwave.commands.thermostatsetpointv3.ThermostatSetpointSet(precision:1, scale:0, scaledValue: nextTemperature, setpointType: 0x01)
    cmds << new hubitat.zwave.commands.thermostatsetpointv3.ThermostatSetpointGet(setpointType:0x01)
    sendToDevice(cmds)
    if (forcePolling==true) {runIn(pollTimer, "poll", [overwrite: true])}
}

private getTemperature (setTemperature, modus) {
    logDebug "getTemperature: ${setTemperature} ${modus}"
    def currentTemperature = 0
    def BigDecimal nextTemperature = 0
    if (modus == "cool") {
        currentTemperature = device.currentValue("coolingSetpoint").toFloat()
    } else {
        currentTemperature = device.currentValue("heatingSetpoint").toFloat()
    }
    if ( Math.abs(currentTemperature - setTemperature) < 0.5 ) {
        if (setTemperature > currentTemperature) {
            nextTemperature = currentTemperature + 0.5
            if (setTemperature >= 28) {
                nextTemperature = 28.0
            }
        }
        if (setTemperature < currentTemperature) {
            nextTemperature = currentTemperature - 0.5
            if (nextTemperature <= 8) {
                nextTemperature = 8.0
            }
        }
        if (setTemperature == currentTemperature) {
            nextTemperature = setTemperature
        }
    } else {
        def Integer temp = Math.round(setTemperature * 10)
        def Integer modul = temp % 5
        nextTemperature = temp - modul
        if (modul >= 3) {
            nextTemperature = nextTemperature + 5
        }
        nextTemperature = nextTemperature / 10
        if (nextTemperature < 8) {nextTemperature = 8}
        if (nextTemperature > 28){nextTemperature = 28}
    }
    return nextTemperature
}

void setThermostatMode(thermostatmode) {
    logDebug "setThermostatMode: ${thermostatmode}"
    switch (thermostatmode) {
        case "emergency heat":
            emergencyHeat()
            break
        case "cool":
            cool()
            break
        case "heat":
            heat()
            break
        case "off":
            off()
            break
        case "auto":
            auto()
            break
        case "manual":
            manual()
            break
        case "calibrate":
            calibrate()
            break
        default:
            log.warn "Unknown thermostat mode ${thermostatmode}"
    }    
    if (forcePolling==true) {runIn(pollTimer, "poll", [overwrite: true])}
}

void lock() {
    logDebug "lock"
    def cmds = []
    cmds << new hubitat.zwave.commands.protectionv1.ProtectionSet(protectionState:0x01)
    cmds << new hubitat.zwave.commands.protectionv1.ProtectionGet()
    sendToDevice(cmds)
}

void unlock() {
    logDebug "unlock"
    def cmds = []
    cmds << new hubitat.zwave.commands.protectionv1.ProtectionSet(protectionState:0x00)
    cmds << new hubitat.zwave.commands.protectionv1.ProtectionGet()
    sendToDevice(cmds)
}

void disableLocalOperations() {
    logDebug "disableLocalOperations"
    lokaleBedinungDeaktiviert()
}

void lokaleBedinungDeaktiviert () {
    logDebug "lokaleBedinungDeaktiviert"
    def cmds = []
    cmds << new hubitat.zwave.commands.protectionv1.ProtectionSet(protectionState:0x02)
    cmds << new hubitat.zwave.commands.protectionv1.ProtectionGet()
    sendToDevice(cmds)
}

void sendConfigurationCommand (List<Integer> zuErneuerndeParametern) {
    logDebug "sendConfigurationCommand: ${zuErneuerndeParametern}"
    def cmds = []
    if (zuErneuerndeParametern) {
    zuErneuerndeParametern.each { k ->
        switch (k) {
            case 1:
                cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:k,    size:1,    scaledConfigurationValue: parameter1 ? 0x01 : 0x00)
                logDebug "Parameter 1 hat den Wert ${parameter1 ? 0x01 : 0x00} übermittelt bekommen"
                break
            case 2:
                cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:k,    size:1,    scaledConfigurationValue: Math.round(parameter2.toFloat()).toInteger())
                logDebug "Parameter 2 hat den Wert ${Math.round(parameter2.toFloat()).toInteger()} übermittelt bekommen"
                break
            case 3:
                cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:k,    size:1,    scaledConfigurationValue: parameter3 ? 0x01 : 0x00)
                logDebug "Parameter 3 hat den Wert ${parameter3 ? 0x01 : 0x00} übermittelt bekommen"
                break
            case 4:
                cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:k,    size:1,    scaledConfigurationValue: parameter4.toInteger())
                logDebug "Parameter 4 hat den Wert ${parameter4.toInteger()} übermittelt bekommen"
                break
            case 5:
                cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:k,    size:1,    scaledConfigurationValue: Math.round(parameter5.toFloat() * 10).toInteger())
                logDebug "Parameter 5 hat den Wert ${Math.round(parameter5.toFloat() * 10).toInteger()} übermittelt bekommen"
                break
            case 6:
                cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:k,    size:1,    scaledConfigurationValue: Math.round(parameter6.toFloat()).toInteger())
                logDebug "Parameter 6 hat den Wert ${Math.round(parameter6.toFloat()).toInteger()} übermittelt bekommen"
                break
            case 7:
                cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:k,    size:1,    scaledConfigurationValue: parameter7.toInteger())
                logDebug "Parameter 7 hat den Wert ${parameter7.toInteger()} übermittelt bekommen"
                break
            case 8:
                if (parameter9) {
                    cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:8,    size:1,    scaledConfigurationValue: -128)
                    logDebug "Parameter 8 hat den Wert -128 übermittelt bekommen"
                } else {
                    cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:8,    size:1,    scaledConfigurationValue: Math.round(parameter8.toFloat() * 10).toInteger())
                    logDebug "Parameter 8 hat den Wert ${Math.round(parameter8.toFloat() * 10).toInteger()} übermittelt bekommen"
                }            
                break
            default:
                logWarn "Falsche Parameternummer für Configuration gesandt"
        }
        cmds << new hubitat.zwave.commands.configurationv1.ConfigurationGet(parameterNumber:k)
    }
    sendToDevice(cmds)
    }
}


// Constants
@Field static final Integer CALIBRATE_IDLE                    = 0
@Field static final Integer CALIBRATE_START                   = 1
@Field static final Integer CALIBRATE_TURN_OFF                = 2
@Field static final Integer CALIBRATE_CHECK_IF_TURNED_OFF     = 3
@Field static final Integer CALIBRATE_TURN_EMERGENCY_HEAT     = 4
@Field static final Integer CALIBRATE_CHECK_IF_TURNED_EMERGENCY_HEAT = 5
@Field static final Integer CALIBRATE_TURN_HEAT               = 6
@Field static final Integer CALIBRATE_CHECK_IF_TURNED_HEAT    = 7
@Field static final Integer CALIBRATE_END                     = 8

@Field static final Integer CALIBRATE_RETRIES_NR              = 2


def calibrate() {
    logInfo "starting calibrate procedure for ${device.displayName} ..."
    state.retries = 0
    runIn (01, 'calibrateStateMachine',  [data: ["state": CALIBRATE_START]])
}

def calibrateStateMachine( Map data ) {
    switch (data.state) {
        case CALIBRATE_IDLE :   
            state.retries = 0
            logDebug "data.state IDLE"
            break
        case CALIBRATE_START :  // 1 starting the calibration state machine
            logDebug "data.state ($data.state) ->  start"
            state.retries = 0
            runIn (01, 'calibrateStateMachine', [data: ["state": CALIBRATE_TURN_EMERGENCY_HEAT]])
            break
            
        case CALIBRATE_TURN_EMERGENCY_HEAT : // turn emergencyHeat
            logInfo "turning emergency heat..."
            log.trace "data.state ($data.state) -> now turning EMERGENCY_HEAT"
            emergencyHeat()    // open the valve 100%
            runIn (10, calibrateStateMachine, [data: ["state": CALIBRATE_CHECK_IF_TURNED_EMERGENCY_HEAT]])
            break        
        case CALIBRATE_CHECK_IF_TURNED_EMERGENCY_HEAT :
            if (device.currentValue("thermostatMode") == "emergency heat" ) {    // TRV has been successfuly swithed to emergency heat in the previous step
                state.retries = 0
                runIn (1, calibrateStateMachine, [data: ["state": CALIBRATE_TURN_OFF]])
            }  else if (state.retries < CALIBRATE_RETRIES_NR) {    // retry
                state.retries = state.retries +1
                logWarn "ERROR turning emergency heat - retrying...($state.retries)"
                runIn (5, calibrateStateMachine, [data: ["state": CALIBRATE_TURN_EMERGENCY_HEAT]])
            } else {
                log.error "ERROR turning emergency heat - GIVING UP!... state is($data.state)"
                state.retries = 0
                runIn (3, calibrateStateMachine, [data: ["state": CALIBRATE_TURN_HEAT]])
            }
            break

            
        case CALIBRATE_TURN_OFF :  // turn off
            logInfo "turning off..."
            off()            // close the valve 100%
            logDebug "data.state ($data.state) -> now turning OFF"
            runIn (10, calibrateStateMachine, [data: ["state": CALIBRATE_CHECK_IF_TURNED_OFF]])
            break
        case CALIBRATE_CHECK_IF_TURNED_OFF :
            if (device.currentValue("thermostatMode") == "off" ) {    // TRV was successfuly turned off in the previous step
                state.retries = 0
                runIn (1, calibrateStateMachine, [data: ["state": CALIBRATE_TURN_HEAT]])
            }
            else if (state.retries < CALIBRATE_RETRIES_NR) {    // retry
                state.retries = state.retries + 1
                logWarn "ERROR turning OFF - retrying...($state.retries )"
                runIn (5, calibrateStateMachine, [data: ["state": CALIBRATE_TURN_OFF]])
            }
            else {
                log.error "ERROR turning OFF - GIVING UP!...state is ($data.state)"
                state.retries = 0
                runIn (3, calibrateStateMachine, [data: ["state": CALIBRATE_TURN_HEAT]])
            }
            break

        case CALIBRATE_TURN_HEAT :     // turn heat (auto)
            logInfo "restoring back to heat/auto..."
            logDebug "data.state ($data.state) -> now turning heat/auto"
            heat()    // back to heat mode
            runIn (10, calibrateStateMachine, [data: ["state": CALIBRATE_CHECK_IF_TURNED_HEAT]])
            break
        case CALIBRATE_CHECK_IF_TURNED_HEAT :
            if (device.currentValue("thermostatMode") == "heat" ) {    // TRV has been successfuly turned to heat mode in the previous step
                state.retries = 0
                runIn (1, calibrateStateMachine, [data: ["state": CALIBRATE_END]])
            }
            else if (state.retries  < CALIBRATE_RETRIES_NR ) {    // retry
                state.retries = state.retries +1
                logWarn "ERROR turning heat - retrying...($state.retries)"
                runIn (5, calibrateStateMachine, [data: ["state": CALIBRATE_TURN_HEAT]])
            }
            else {
                state.retries = 0
                log.error "ERROR turning emergencyHeat - GIVING UP !... state is($data.state)"
                runIn (3, calibrateStateMachine, [data: ["state": CALIBRATE_END]])
            }
            break


        case CALIBRATE_END :   // verify if back to heat (auto)
            if (device.currentValue("thermostatMode") == "heat" ) {    // TRV has been successfuly turned to heat/auto
                logInfo "calibratrion  finished successfuly"
                logDebug "data.state ($data.state) ->  finished successfuly"
            }
            else {
                log.error "ERROR CALIBRATE_END - GIVING UP!... state is ($data.state)"
            }
            state.retries = 0
            unschedule(calibrateStateMachine)
            break
        default :
            log.error "state calibrate UNKNOWN = ${data.state}"
            state.retries = 0
            unschedule(calibrateStateMachine)
            break
    }
}

void updated() {
    log.info "Device ${device.label?device.label:device.name} is updated"
    scheduleDeviceHealthCheck()
    if (logEnable==true) {
        runIn(86400, logsOff, [overwrite: true])    // turn off debug logging after 24 hours
        logInfo "Debug logging will be turned off after 24 hours"
    }
    else {
        unschedule(logsOff)
    }    
    def cmds = []
    cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:1,    size:1,    scaledConfigurationValue: parameter1 ? 0x01 : 0x00)
    logInfo englishLang ? "Parameter 1 will be set to ${parameter1 ? 0x01 : 0x00}" : "Parameter 1 hat den Wert ${parameter1 ? 0x01 : 0x00} übermittelt bekommen"
    cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:2,    size:1,    scaledConfigurationValue: Math.round(parameter2.toFloat()).toInteger())
    logInfo englishLang ? "Parameter 2 will be set to ${Math.round(parameter2.toFloat()).toInteger()}" : "Parameter 2 hat den Wert ${Math.round(parameter2.toFloat()).toInteger()} übermittelt bekommen"
    cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:3,    size:1,    scaledConfigurationValue: parameter3 ? 0x01 : 0x00)
    logInfo englishLang ? "Parameter 3 will be set to ${parameter3 ? 0x01 : 0x00}" : "Parameter 3 hat den Wert ${parameter3 ? 0x01 : 0x00} übermittelt bekommen"
    cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:4,    size:1,    scaledConfigurationValue: parameter4.toInteger())
    logInfo englishLang ? "Parameter 4 will be set to ${parameter4.toInteger()}" : "Parameter 4 hat den Wert ${parameter4.toInteger()} übermittelt bekommen"
    cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:5,    size:1,    scaledConfigurationValue: Math.round(parameter5.toFloat() * 10).toInteger())
    logInfo englishLang ? "Parameter 5 will be set to ${Math.round(parameter5.toFloat() * 10).toInteger()}" : "Parameter 5 hat den Wert ${Math.round(parameter5.toFloat() * 10).toInteger()} übermittelt bekommen"
    cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:6,    size:1,    scaledConfigurationValue: Math.round(parameter6.toFloat()).toInteger())
    logInfo englishLang ? "Parameter 6 will be set to ${Math.round(parameter6.toFloat()).toInteger()}" : "Parameter 6 hat den Wert ${Math.round(parameter6.toFloat()).toInteger()} übermittelt bekommen"
    cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:7,    size:1,    scaledConfigurationValue: parameter7.toInteger())
    logInfo englishLang ? "Parameter 7 will be set to ${parameter7.toInteger()}" : "Parameter 7 hat den Wert ${parameter7.toInteger()} übermittelt bekommen"
    if (parameter9) {
        cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:8,    size:1,    scaledConfigurationValue: -128)
        logInfo englishLang ? "Parameter 8 will be set to -128" : "Parameter 8 hat den Wert -128 übermittelt bekommen"
    } else {
        cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:8,    size:1,    scaledConfigurationValue: Math.round(parameter8.toFloat() * 10).toInteger())
        logInfo englishLang ? "Parameter 8 will be set to ${Math.round(parameter8.toFloat() * 10).toInteger()}" : "Parameter 8 hat den Wert ${Math.round(parameter8.toFloat() * 10).toInteger()} übermittelt bekommen"
    }
    for (int i=1 ; i<=8 ; i++) {
        cmds << new hubitat.zwave.commands.configurationv1.ConfigurationGet(parameterNumber: i)
    }
    sendToDevice(cmds)
    autoRefresh()
}

void installed() {
    log.info "Device ${device.label?device.label:device.name} is installed"
    sendEvent(name:"Notifity",                        value:"Installed", displayed: true)
    sendEvent(name:"deviceResetLocally",            value:false, displayed: true)
    sendEvent(name:"supportedThermostatFanModes",     value: groovy.json.JsonOutput.toJson(["circulate"]), isStateChange: true)
    sendEvent(name:"supportedThermostatModes",        value: groovy.json.JsonOutput.toJson(["off", "heat", "emergency heat", "cool", "manual", "calibrate"]), isStateChange: true)
    def cmds = []
    cmds << new hubitat.zwave.commands.protectionv1.ProtectionGet()
    cmds << new hubitat.zwave.commands.thermostatsetpointv3.ThermostatSetpointGet(setpointType:0x01)
    cmds << new hubitat.zwave.commands.thermostatsetpointv3.ThermostatSetpointGet(setpointType:0x0B)
    cmds << new hubitat.zwave.commands.batteryv1.BatteryGet()
    cmds << new hubitat.zwave.commands.thermostatmodev3.ThermostatModeGet()
    cmds << new hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelGet(sensorType:1)
    for (int i=1 ; i<=8 ; i++) {
        cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber: i, defaultValue: true)
        logInfo englishLang ? "Parameter number ${i} is reset" : "Parameter nummer ${i} ist zurückgesetzt"
    }
    sendToDevice(cmds)
}

def setDeviceLimits() { // for google and amazon compatability
    sendEvent(name:"minHeatingSetpoint", value: settings.tempMin ?: 8, unit: "°C", isStateChange: true, displayed: false)
    sendEvent(name:"maxHeatingSetpoint", value: settings.tempMax ?: 28, unit: "°C", isStateChange: true, displayed: false)
    logDebug "setDeviceLimits - device max/min set"
}    

def logsOff(){
    if (settings?.logEnable) log.info "${device.displayName} debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def initialize() {
    log.info "initialize..."
    state.retries = 0
    setDeviceLimits()
    installed()
}


void sendToDevice(List<hubitat.zwave.Command> cmds, Long delay=1000) {
    logDebug "sendToDevice: $cmds"
    sendHubCommand(new hubitat.device.HubMultiAction(commands(cmds, delay), hubitat.device.Protocol.ZWAVE))
}

void sendToDevice(hubitat.zwave.Command cmd, Long delay=1000) {
    logDebug "sendToDevice: $cmd"
    sendHubCommand(new hubitat.device.HubAction(zwaveSecureEncap(cmd.format()), hubitat.device.Protocol.ZWAVE))
}

List<String> commands(List<hubitat.zwave.Command> cmds, Long delay=1000) {
    logDebug "sendToDevice: $cmds"
    return delayBetween(cmds.collect{ zwaveSecureEncap(it.format()) }, delay)
}

void fanOn() {
    logDebug "fanOn"
    sendToDevice (new hubitat.zwave.commands.batteryv1.BatteryGet())
    logInfo "Battery wird abgefragt"
}

void auto() {
    logWarn "auto is not implemented!"
}

void fanAuto() {
    logWarn "fanAuto is not implemented!"
}

void fanCirculate() {
    logDebug "fanCirculate"
    sendEvent(name: "thermostatFanMode", value: "circulate", displayed: true)
}

void setThermostatFanMode(fanmode) {
    logDebug "setThermostatFanMode: ${fanmode}"
    sendEvent(name: "thermostatFanMode", value: "circulate", displayed: true)
}

void ExternalSensorTemperature(temperature) {
    logDebug "ExternalSensorTemperature: ${temperature}"
    def Integer x = Math.round(temperature * 100) % 256
    def Integer y = (Math.round(temperature * 100) - x) / 256    
    sendToDevice(new hubitat.zwave.commands.sensormultilevelv10.SensorMultilevelReport(precision:2,scale:0,sensorType:1,sensorValue:[y,x],size:2,scaledSensorValue:(Math.round(temperature * 100)/100).toBigDecimal()))
}

void poll() {
    logDebug "poll"
    def cmds = []
    cmds << new hubitat.zwave.commands.batteryv1.BatteryGet()
    cmds << new hubitat.zwave.commands.thermostatmodev3.ThermostatModeGet()
    cmds << new hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelGet()
    sendToDevice(cmds)
}

void refresh() {
    def cmds = []
    cmds << new hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelGet()                // valve + simulated OperatingState calculation depending on valve % open
    cmds << new hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelGet(sensorType:1)    // temperature
    cmds << new hubitat.zwave.commands.thermostatmodev3.ThermostatModeGet()                    // operation mode (heat, cool, ...)
    cmds << new hubitat.zwave.commands.thermostatsetpointv3.ThermostatSetpointGet(setpointType:0x01)    // heatingSetpoint 
    sendToDevice(cmds)
    logInfo "Refreshing..."    
}

void autoRefresh() {
    logDebug "autoRefresh"
    unschedule(refresh)
    if (refreshRate != null ) {
        switch(refreshRate) {
            case "1" :
                runEvery1Minute(refresh)
                break
            case "5" :
                runEvery5Minutes(refresh)
                break
            case "10" :
                runEvery10Minutes(refresh)
                break
            case "15" :
                runEvery15Minutes(refresh)
                break
            case "30" :
                runEvery30Minutes(refresh)
                break
            case "60" :
                runEvery1Hour(refresh)
                break
            case "0" :
            default :
                unschedule(refresh)
                log.info "Auto Refresh off"
                return
        }
        logInfo "Refresh Scheduled for every ${refreshRate} minutes"
    }
}

def driverVersionAndTimeStamp() {version()+' '+timeStamp()}

def checkDriverVersion() {
    if (state.driverVersion == null || driverVersionAndTimeStamp() != state.driverVersion) {
        logInfo "updating the settings from the current driver version ${state.driverVersion} to the new version ${driverVersionAndTimeStamp()}"
        scheduleDeviceHealthCheck()
        state.driverVersion = driverVersionAndTimeStamp()
    }
}

void scheduleDeviceHealthCheck() {
    Random rnd = new Random()
    //schedule("1 * * * * ? *", 'deviceHealthCheck') // for quick test
    schedule("${rnd.nextInt(59)} ${rnd.nextInt(59)} 1/3 * * ? *", 'deviceHealthCheck')
}

// called when any event was received from the Zigbee device in parse() method..
def setPresent() {
    if ((device.currentValue("healthStatus") ?: "unknown") != "online") {
        sendHealthStatusEvent("online")
        logInfo "is present"
    }    
    state.notPresentCounter = 0    
}

def deviceHealthCheck() {
    state.notPresentCounter = (state.notPresentCounter ?: 0) + 1
    if (state.notPresentCounter > presenceCountTreshold) {
        if ((device.currentValue("healthStatus", true) ?: "unknown") != "offline" ) {
            sendHealthStatusEvent("offline")
            if (settings?.txtEnable) { log.warn "${device.displayName} is not present!" }
            // TODO - send alarm ?
        }
    }
    else {
        logDebug "deviceHealthCheck - online (notPresentCounter=${state.notPresentCounter})"
    }
    
}

def sendHealthStatusEvent(value) {
    sendEvent(name: "healthStatus", value: value, descriptionText: "${device.displayName} healthStatus set to $value")
}


def logDebug(msg) {
    if (settings?.logEnable) {
        log.debug "${device.displayName} " + msg
    }
}

def logInfo(msg) {
    if (settings?.txtEnable) {
        log.info "${device.displayName} " + msg
    }
}

def logWarn(msg) {
    if (settings?.txtEnable) {
        log.warn "${device.displayName} " + msg
    }
}
