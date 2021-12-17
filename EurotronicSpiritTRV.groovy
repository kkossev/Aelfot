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
*
*/

import groovy.transform.Field
import hubitat.helper.HexUtils
import hubitat.device.HubAction

metadata {
	definition (name: "Eurotronic Spirit TRV (aelfot KK mod)", namespace: "aelfot", author: "Ravil Rubashkin") {
		capability "Battery"
		capability "Lock"
		capability "Thermostat"
		capability "Actuator"
		capability "Sensor"
		capability "TemperatureMeasurement"
		capability "Polling"
		capability "SwitchLevel"
        capability "Refresh"
        capability "Initialize"

		attribute "ExterneTemperatur", "string"
		attribute "Notifity",			"string"
		attribute "DeviceResetLocally",	"bool"

		command "SendTemperature", [[name: "Temperature", type: "NUMBER", description:""]]
		command "manual"
        command "disableLocalOperations"    //"lokaleBedinungDeaktiviert"
        command "calibrate"


		fingerprint  mfr:"0148", prod:"0003", deviceId:"0001", inClusters:"0x5E,0x55,0x98,0x9F"
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

    
	preferences {
		input name:"englishLang",    	type:"bool",	title: "English Language",		    		description: "Default: No",					            defaultValue:false
        if (englishLang==true) {
    		input name: "parameter1",	type:"bool",	title: "Invert LCD",				        description: "Default: No",					            defaultValue:false
    		input name: "parameter2",	type:"number",	title: "LCD Timeout (in secs)",			    description: "Default: 0-Always on, range 0..30",       defaultValue:0,		range: "0..30"
    		input name: "parameter3",	type:"bool",	title: "Backlight",			                description: "Default: Deactivated",			        defaultValue:false
    		input name: "parameter4",	type:"enum",	title: "Battery reporting",				    description: "Default: once a day",			            defaultValue:1,		options: batteriestatus
    		input name: "parameter5",	type:"number",	title: "Temperature reporting",	            description: "Default: 0.5° change, range 0.0..5.0",    defaultValue:0.5,	range: "0.0..5.0"
    		input name: "parameter6",	type:"number",	title: "Valve reporting",		            description: "Default: Deactivated, range: 0..100",	    defaultValue:0,		range: "0..100"
    		input name: "parameter7",	type:"enum",	title: "Window Open Detection",			    description: "Default: Medium sensitivity",	            defaultValue:2,		options: windowDetectOptions
    		input name: "parameter8",	type:"number",	title: "Temperature offset",				description: "Default: no correction. range: -5.0..5.0",defaultValue:0,		range: "-5.0..5.0"
    		input name: "parameter9",	type:"bool",	title: "Use external temperature sensor?",	description: "Default: No",						        defaultValue:false
    		input name: "forceStateChange",type:"bool",	title: "Force State Change",	        	description: "Default: No (used for better graphs only)",defaultValue:false
    		input name: "refreshRate",  type: "enum",   title: "Refresh rate",                      description: "Select refresh rate",                     defaultValue: "0", required: false, options: refreshRates
    		input name: "lg",			type:"bool",	title: "Debug Logging",			        	description: "Default: No",						        defaultValue:false
        }
        else
        {
    		input name: "parameter1",	type:"bool",	title: "Display invertieren?",				description: "Default: Nein",					defaultValue:false
    		input name: "parameter2",	type:"number",	title: "Display ausschalten nach",			description: "Default: Immer an(0)",			defaultValue:0,		range: "0..30"
    		input name: "parameter3",	type:"bool",	title: "Hintergrundbeleuchtung",			description: "Default: Deaktiviert",			defaultValue:false
    		input name: "parameter4",	type:"enum",	title: "Batteryabfrage",					description: "Default: 1 mal täglich",			defaultValue:1,		options: batteriestatus
    		input name: "parameter5",	type:"number",	title: "Meldung bei Temperaturdifferenz",	description: "Default: bei Delta 0.5°", 		defaultValue:0.5,	range: "0.0..5.0"
    		input name: "parameter6",	type:"number",	title: "Meldung bei Valvedifferenz",		description: "Default: Deaktiviert",			defaultValue:0,		range: "0..100"
    		input name: "parameter7",	type:"enum",	title: "Fensteroffnungserkennung",			description: "Default: Empfindlichkeit mittel",	defaultValue:2,		options: windowDetectOptions
    		input name: "parameter8",	type:"number",	title: "Temperature offset",				description: "Default: Keine Korrektur",		defaultValue:0,		range: "-5.0..5.0"
    		input name: "parameter9",	type:"bool",	title: "Temperatur extern bereitgestellt?",	description: "Default: Nein",					defaultValue:false
    		input name: "forceStateChange",type:"bool",	title: "Force State Change",	        	description: "Default: Nein (nur für bessere Grafiken verwendet)",defaultValue:false
    		input name: "refreshRate",  type: "enum",   title: "Aktualisierungsrate",               description: "Default: Nein",                   defaultValue: "0", required: false, options: refreshRates
    		input name: "lg",			type:"bool",	title: "Logging on/off",					description: "",								defaultValue:false
        }            
	}
}

@Field static Map commandClassVersions =
	[0x85:2,	//Association
	 0x59:1,	//Association Group Information
	 0x20:1,	//Basic
	 0x80:1,	//Battery
	 0x70:1,	//Configuration
	 0x5A:1,	//Device Reset Locally
	 0x7A:3,	//Firmware Update Md V3
	 0x72:1,	//Manufacturer Specific
	 0x31:5,	//Multilevel Sensor
	 0x26:1,	//Multilevel Switch
	 0x71:8,	//Notifikation
	 0x73:1,	//Power Level
	 0x75:1,	//Protection
	 0x98:2,	//Security ohne verschlüsselung
	 0x40:3,	//Thermostat Mode
	 0x43:3,	//Thermostat Setpoint
	 0x55:2,	//Transport Service ohne verschlüsselung
	 0x86:2,	//Version
	 0x5E:2]	//Z-Wave Plus Info ohne verschlüsselung

def parse(String description) {
	def cmd = zwave.parse(description, commandClassVersions)
	if (cmd) {
		return zwaveEvent(cmd)
	} else {
		log.debug "Non-parsed event: ${description}"
	}
}

void zwaveEvent (hubitat.zwave.commands.notificationv8.NotificationReport cmd) {
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
	if (lg) log.info englishLang==true ? "Notifikaiton is ${resultat.value}" : "Notifikaiton ist ${resultat.value}"
    if (forceStateChange==true) {resultat.isStateChange = true}
	sendEvent(resultat)
}

void zwaveEvent (hubitat.zwave.commands.thermostatsetpointv3.ThermostatSetpointReport cmd) {
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
    if (lg) log.info englishLang==true ? "Thermostat report: ${resultat.name} is ${resultat.value} ${resultat.unit}" : "Thermostat hat den ${resultat.name} Report ${resultat.value} ${resultat.unit}"
    if (forceStateChange==true) {resultat.isStateChange = true}
	sendEvent(resultat)
}

void zwaveEvent (hubitat.zwave.commands.batteryv1.BatteryReport cmd) {
	if (lg) log.info englishLang==true ? "Battery report ${cmd.batteryLevel} %" : "batteryreport ist ${cmd.batteryLevel} %"
	sendEvent(name:"battery", value: cmd.batteryLevel, unit: "%", displayed: true, isStateChange: true)
}

void zwaveEvent(hubitat.zwave.commands.deviceresetlocallyv1.DeviceResetLocallyNotification cmd) {
    if (lg) log.warn englishLang==true ? "Device Reset Locally" : "Device Reset Locally"
	sendEvent(name:"DeviceResetLocally", value: true, displayed = true, isStateChange: true)
}

void zwaveEvent (hubitat.zwave.commands.protectionv1.ProtectionReport cmd) {
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
	if (lg) log.info englishLang==true ? "protection report is ${resultat.value}" :"protection report ist ${resultat.value}"
}

void zwaveEvent (hubitat.zwave.commands.thermostatmodev3.ThermostatModeReport cmd) {
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
	if (lg) log.info englishLang==true ? "Thermostat reported mode is ${resultat.value}" : "thermostat hat den mode gemeldet ${resultat.value}"
}

void zwaveEvent (hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd) {
	def resultat = [:]
	resultat.value = cmd.scaledSensorValue
	resultat.name = "temperature"
	resultat.unit = cmd.scale == 1 ? "°F" : "°C"
	resultat.displayed = true
    if (lg) log.info englishLang==true ? "temperature is ${resultat.value} ${resultat.unit}" : "temperature ist ${resultat.value} ${resultat.unit}"
    if (forceStateChange==true) {resultat.isStateChange = true}
	sendEvent(resultat)
}

void thermostatLevelAndOperatingStateEvents (valvePos)
{
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
	if (lg) log.info englishLang==true ? "Valve position is ${valvePosition} %" : "Valveposition ist ${valvePosition} %"
	if (lg) log.info englishLang==true ? "Operating state is ${resultat.value}" : "Operating state ist ${resultat.value}"
    if (forceStateChange==true) {resultat.isStateChange = true}
	sendEvent(name:"level", value: valvePosition, isStateChange: true, unit: "%")
	sendEvent(resultat)    
    
}

void zwaveEvent (hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelReport cmd) {
    thermostatLevelAndOperatingStateEvents(cmd.value)
}

void zwaveEvent(hubitat.zwave.Command cmd) {
	log.debug englishLang==true ? "${device.displayName}: Unhandled: $cmd" : "${device.displayName}: Unhandled: $cmd"
}

void off() {
	def cmds = []
	cmds << new hubitat.zwave.commands.thermostatmodev3.ThermostatModeSet(mode:0x00)
	cmds << new hubitat.zwave.commands.thermostatmodev3.ThermostatModeGet()
	sendToDevice(cmds)
    sendEvent(name:"thermostatOperatingState", value: "idle", isStateChange: true)
}

void heat() {
	def cmds = []
	cmds << new hubitat.zwave.commands.thermostatmodev3.ThermostatModeSet(mode:0x01)
	cmds << new hubitat.zwave.commands.thermostatmodev3.ThermostatModeGet()
	cmds << new hubitat.zwave.commands.thermostatsetpointv3.ThermostatSetpointGet(setpointType:0x01)
	sendToDevice(cmds)
}

void cool() {
	def cmds = []
	cmds << new hubitat.zwave.commands.thermostatmodev3.ThermostatModeSet(mode:0x0B)
	cmds << new hubitat.zwave.commands.thermostatmodev3.ThermostatModeGet()
	cmds << new hubitat.zwave.commands.thermostatsetpointv3.ThermostatSetpointGet(setpointType:0x0B)
	sendToDevice(cmds)
}

void emergencyHeat() {
	def cmds = []
	cmds << new hubitat.zwave.commands.thermostatmodev3.ThermostatModeSet(mode:0x0F)
	cmds << new hubitat.zwave.commands.thermostatmodev3.ThermostatModeGet()
	sendToDevice(cmds)
    sendEvent(name:"thermostatOperatingState", value: "heating", isStateChange: true)
}

void manual() {
	def cmds = []
	cmds << new hubitat.zwave.commands.thermostatmodev3.ThermostatModeSet(mode:0x1F)
	cmds << new hubitat.zwave.commands.thermostatmodev3.ThermostatModeGet()
	cmds << new hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelSet(value: 0)
	cmds << new hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelGet()
	sendToDevice(cmds)
}

void zwaveEvent (hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
	def cmds = []
	switch (cmd.parameterNumber) {
		case 1:
    		if ((parameter1 ? 0x01 : 0x00) != cmd.scaledConfigurationValue) {
    			if (lg) log.info englishLang==true ? "Parameter number ${cmd.parameterNumber} was not set, trying again" : "Parameter nummer ${cmd.parameterNumber} hat den Wert nich übernommen, erneter Versuch"
    			cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:1,	size:1,	scaledConfigurationValue: parameter1 ? 0x01 : 0x00)
    		} else {
    			if (lg) log.info englishLang==true ? "Parameter number  ${cmd.parameterNumber} was successfuly set" : "Parameter nummer  ${cmd.parameterNumber} hat den Wert erfolgreich übernommen"
    		}
    		break;
		case 2:
    		if (Math.round(parameter2).toInteger() != cmd.scaledConfigurationValue) {
    			if (lg) log.info englishLang==true ? "Parameter number ${cmd.parameterNumber} was not set, trying again" : "Parameter nummer ${cmd.parameterNumber} hat den Wert nich übernommen, erneter Versuch"
    			cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:2,	size:1,	scaledConfigurationValue: Math.round(parameter2).toInteger())
    		} else {
    			if (lg) log.info englishLang==true ? "Parameter number  ${cmd.parameterNumber} was successfuly set" : "Parameter nummer  ${cmd.parameterNumber} hat den Wert erfolgreich übernommen"
    		}
    		break;
		case 3:
    		if ((parameter3 ? 0x01 : 0x00) != cmd.scaledConfigurationValue) {
    			if (lg) log.info englishLang==true ? "Parameter number ${cmd.parameterNumber} was not set, trying again" : "Parameter nummer ${cmd.parameterNumber} hat den Wert nich übernommen, erneter Versuch"
    			cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:3,	size:1,	scaledConfigurationValue: parameter3 ? 0x01 : 0x00)
    		} else {
    			if (lg) log.info englishLang==true ? "Parameter number  ${cmd.parameterNumber} was successfuly set" : "Parameter nummer  ${cmd.parameterNumber} hat den Wert erfolgreich übernommen"
    		}
    		break;
		case 4:
    		if (parameter4.toInteger() != cmd.scaledConfigurationValue) {
    			if (lg) log.info englishLang==true ? "Parameter number ${cmd.parameterNumber} was not set, trying again" : "Parameter nummer ${cmd.parameterNumber} hat den Wert nich übernommen, erneter Versuch"
    			cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:4,	size:1,	scaledConfigurationValue: parameter4.toInteger())
    		} else {
    			if (lg) log.info englishLang==true ? "Parameter number  ${cmd.parameterNumber} was successfuly set" : "Parameter nummer  ${cmd.parameterNumber} hat den Wert erfolgreich übernommen"
    		}
    		break;
		case 5:
    		if (Math.round(parameter5.toFloat() * 10).toInteger() != cmd.scaledConfigurationValue) {
    			if (lg) log.info englishLang==true ? "Parameter number ${cmd.parameterNumber} was not set, trying again" : "Parameter nummer ${cmd.parameterNumber} hat den Wert nich übernommen, erneter Versuch"
    			cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:5,	size:1,	scaledConfigurationValue: Math.round(parameter5.toFloat() * 10).toInteger())
    		} else {
    			if (lg) log.info englishLang==true ? "Parameter number  ${cmd.parameterNumber} was successfuly set" : "Parameter nummer  ${cmd.parameterNumber} hat den Wert erfolgreich übernommen"
    		}
    		break;
		case 6:
    		if (Math.round(parameter6).toInteger() != cmd.scaledConfigurationValue) {
    			if (lg) log.info englishLang==true ? "Parameter number ${cmd.parameterNumber} was not set, trying again" : "Parameter nummer ${cmd.parameterNumber} hat den Wert nich übernommen, erneter Versuch"
    			cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:6,	size:1,	scaledConfigurationValue: Math.round(parameter6).toInteger())
    		} else {
    			if (lg) log.info englishLang==true ? "Parameter number  ${cmd.parameterNumber} was successfuly set" : "Parameter nummer  ${cmd.parameterNumber} hat den Wert erfolgreich übernommen"
    		}
    		break;
		case 7:
    		if (parameter7.toInteger() != cmd.scaledConfigurationValue) {
    			if (lg) log.info englishLang==true ? "Parameter number ${cmd.parameterNumber} was not set, trying again" : "Parameter nummer ${cmd.parameterNumber} hat den Wert nich übernommen, erneter Versuch"
    			cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:7,	size:1,	scaledConfigurationValue: parameter7.toInteger())
    		} else {
    			if (lg) log.info englishLang==true ? "Parameter number  ${cmd.parameterNumber} was successfuly set" : "Parameter nummer  ${cmd.parameterNumber} hat den Wert erfolgreich übernommen"
    		}
    		break;
		case 8:
    		if (parameter9) {
    			if (cmd.scaledConfigurationValue != -128) {
    				if (lg) log.info englishLang==true ? "Parameter number 9 was not set, trying again" : "Parameter nummer 9 hat den Wert nich übernommen, erneter Versuch"
    				cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:8,	size:1,	scaledConfigurationValue: -128)
    			} else {
    				if (lg) log.info englishLang==true ? "Parameter number 8 was successfuly set" : "Parameter nummer 8 hat den Wert erfolgreich übernommen"
    				if (lg) log.info englishLang==true ? "Parameter number 9 was successfuly set" : "Parameter nummer 9 hat den Wert erfolgreich übernommen"
    				sendEvent (name: "ExterneTemperatur", value: "true")
    			}
    		} else  {
    			if (cmd.scaledConfigurationValue != Math.round(parameter8.toFloat() * 10).toInteger()) {
    				if (lg) log.info englishLang==true ? "Parameter number ${cmd.parameterNumber} was not set, trying again" : "Parameter nummer ${cmd.parameterNumber} hat den Wert nich übernommen, erneter Versuch"
    				cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:8,	size:1,	scaledConfigurationValue: Math.round(parameter8.toFloat() * 10).toInteger())
    			} else {
    				if (lg) log.info englishLang==true ? "Parameter number 8 was successfuly set" : "Parameter nummer 8 hat den Wert erfolgreich übernommen"
    				if (lg) log.info englishLang==true ? "Parameter number 9 was successfuly set" : "Parameter nummer 9 hat den Wert erfolgreich übernommen"
    				sendEvent (name: "ExterneTemperatur", value: "false")
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
		if (lg) log.info englishLang==true ? "Valve was set to ${val} %" : "Die Valve wird auf den Wert ${val} gestellt"
	} else {
		sendToDevice(new hubitat.zwave.commands.switchmultilevelv1.SwitchMultilevelGet())
		if (lg) log.info englishLang==true ? "Valve opening position can only be set in manual mode" : "Eine Einstellung der Valveöffnung ist nur im manual modus möglich"
	}
}

void setCoolingSetpoint(temperature) {
	def nextTemperature = getTemperature (temperature,"cool")
	sendEvent(name: "coolingSetpoint", value: nextTemperature.toFloat(), displayed: true)
	def cmds = []
	cmds << new hubitat.zwave.commands.thermostatsetpointv3.ThermostatSetpointSet(precision:1, scale:0, scaledValue: nextTemperature, setpointType: 0x0B)
	cmds << new hubitat.zwave.commands.thermostatsetpointv3.ThermostatSetpointGet(setpointType:0x0B)
	sendToDevice(cmds)
}

void setHeatingSetpoint(temperature) {
	def nextTemperature = getTemperature (temperature,"heat")
	sendEvent(name: "heatingSetpoint", value: nextTemperature.toFloat(), displayed: true, unit: cmd.scale == 1 ? "°F" : "°C")
	def cmds = []
	cmds << new hubitat.zwave.commands.thermostatsetpointv3.ThermostatSetpointSet(precision:1, scale:0, scaledValue: nextTemperature, setpointType: 0x01)
	cmds << new hubitat.zwave.commands.thermostatsetpointv3.ThermostatSetpointGet(setpointType:0x01)
	sendToDevice(cmds)
}

private getTemperature (setTemperature, modus) {
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
	switch (thermostatmode) {
		case "emergency heat":
		    emergencyHeat()
		    break;
		case "cool":
		    cool()
		    break;
		case "heat":
            heat()
		    break;
        case "off":
		    off()
		    break;
		case "auto":
		    auto()
		    break;
		case "manual":
		    manual()
		    break;
	}
}

void lock() {
	def cmds = []
	cmds << new hubitat.zwave.commands.protectionv1.ProtectionSet(protectionState:0x01)
	cmds << new hubitat.zwave.commands.protectionv1.ProtectionGet()
	sendToDevice(cmds)
}

void unlock() {
	def cmds = []
	cmds << new hubitat.zwave.commands.protectionv1.ProtectionSet(protectionState:0x00)
	cmds << new hubitat.zwave.commands.protectionv1.ProtectionGet()
	sendToDevice(cmds)
}

void disableLocalOperations() {
    lokaleBedinungDeaktiviert()
}

void lokaleBedinungDeaktiviert () {
	def cmds = []
	cmds << new hubitat.zwave.commands.protectionv1.ProtectionSet(protectionState:0x02)
	cmds << new hubitat.zwave.commands.protectionv1.ProtectionGet()
	sendToDevice(cmds)
}

void poll() {
	def cmds = []
	cmds << new hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelGet()                // valve and simulated OperatingState 
	cmds << new hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelGet(sensorType:1)    // temperature
	sendToDevice(cmds)
	if (lg) log.info "Polling..."
}

void refresh() {
	def cmds = []
	cmds << new hubitat.zwave.commands.switchmultilevelv3.SwitchMultilevelGet()                // valve and simulated OperatingState 
	cmds << new hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelGet(sensorType:1)    // temperature
    cmds << new hubitat.zwave.commands.thermostatmodev3.ThermostatModeGet()                    // operation mode (heat, cool, ...)
	cmds << new hubitat.zwave.commands.thermostatsetpointv3.ThermostatSetpointGet(setpointType:0x01)    // heatingSetpoint 
	//cmds << new hubitat.zwave.commands.thermostatsetpointv3.ThermostatSetpointGet(setpointType:0x0B)    // coolingSetpoint - not needed!
	sendToDevice(cmds)
	if (lg) log.info "Refreshing..."    
}

void autoRefresh() {
	unschedule(refresh)
    if (refreshRate != null ) {
        switch(refreshRate) {
    	    case "1" :
    		    runEvery1Minute(refresh)
    			log.info "Refresh Scheduled for every minute"
    			break
    		case "15" :
    			runEvery15Minutes(refresh)
    			log.info "Refresh Scheduled for every 15 minutes"
    			break
    		case "10" :
    			runEvery10Minutes(refresh)
    			log.info "Refresh Scheduled for every 10 minutes"
    			break
    		case "5" :
    			runEvery5Minutes(refresh)
    			log.info "Refresh Scheduled for every 5 minutes"
    			break
    		case "0" :
            default :
       			unschedule(refresh)
    			log.info "Auto Refresh off"
        }
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

@Field static final Integer CALIBRATE_RETRIES_NR              = 3


def calibrate() {
    log.debug "starting calibrate() for ${device.displayName} .."
    runIn (01, 'calibrateStateMachine',  [data: ["state": CALIBRATE_START, "retry": 0]])
}

def calibrateStateMachine( Map data ) {
    switch (data.state) {
        case CALIBRATE_IDLE :   
            log.trace "data.state IDLE"
            break
        case CALIBRATE_START :  // 1 starting the calibration state machine
            log.debug "data.state ($data.state) ->  start"
            runIn (01, 'calibrateStateMachine', [data: ["state": CALIBRATE_TURN_OFF, "retry": 0]])
            break
        case CALIBRATE_TURN_OFF :  // turn off
            off()            // close the valve 100%
            log.debug "data.state ($data.state) -> now turning OFF"
            runIn (5, calibrateStateMachine, [data: ["state": CALIBRATE_CHECK_IF_TURNED_OFF, "retry": 0]])
            break
        case CALIBRATE_CHECK_IF_TURNED_OFF :
            if (device.currentValue("thermostatMode") == "off" ) {    // TRV was successfuly turned off in the previous step
                runIn (1, calibrateStateMachine, [data: ["state": CALIBRATE_TURN_EMERGENCY_HEAT, "retry": 0]])
            }
            else if (data.retry < CALIBRATE_RETRIES_NR) {    // retry
                log.warn "ERROR turning OFF - retrying...($data.retry)"
                runIn (5, calibrateStateMachine, [data: ["state": CALIBRATE_TURN_OFF, "retry": data.retry+1]])
            }
            else {
                log.error "ERROR turning OFF - GIVING UP!...state is ($data.state)"
                runIn (3, calibrateStateMachine, [data: ["state": CALIBRATE_TURN_HEAT, "retry": 0]])
            }
            break
        case CALIBRATE_TURN_EMERGENCY_HEAT : // turn emergencyHeat
            log.trace "data.state ($data.state) -> now turning EMERGENCY_HEAT"
            emergencyHeat()    // open the valve 100%
            runIn (5, calibrateStateMachine, [data: ["state": CALIBRATE_CHECK_IF_TURNED_EMERGENCY_HEAT, "retry": 0]])
            break        
        case CALIBRATE_CHECK_IF_TURNED_EMERGENCY_HEAT :
            if (device.currentValue("thermostatMode") == "emergency heat" ) {    // TRV has been successfuly swithed to emergency heat in the previous step
                runIn (1, calibrateStateMachine, [data: ["state": CALIBRATE_TURN_HEAT, "retry": 0]])
            }  else if (data.retry < CALIBRATE_RETRIES_NR) {    // retry
                log.error "ERROR turning emergency heat - retrying...($data.retry)"
                runIn (5, calibrateStateMachine, [data: ["state": CALIBRATE_TURN_EMERGENCY_HEAT, "retry": data.retry+1]])
            } else {
                log.error "ERROR turning emergency heat - GIVING UP!... state is($data.state)"
                runIn (3, calibrateStateMachine, [data: ["state": CALIBRATE_TURN_HEAT, "retry": 0]])
            }
            break
        case CALIBRATE_TURN_HEAT :     // turn heat (auto)
            log.debug "data.state ($data.state) -> now turning heat/auto"
            heat()    // back to heat mode
            runIn (5, calibrateStateMachine, [data: ["state": CALIBRATE_CHECK_IF_TURNED_HEAT, "retry": 0]])
            break
        case CALIBRATE_CHECK_IF_TURNED_HEAT :
            if (device.currentValue("thermostatMode") == "heat" ) {    // TRV has been successfuly turned to heat mode in the previous step
                runIn (10, calibrateStateMachine, [data: ["state": CALIBRATE_END, "retry": 0]])
            }
            else if (data.retry < CALIBRATE_RETRIES_NR ) {    // retry
                log.warn "ERROR turning heat - retrying...($data.retry)"
                runIn (5, calibrateStateMachine, [data: ["state": CALIBRATE_TURN_HEAT, "retry": data.retry+1]])
            }
            else {
                log.error "ERROR turning emergencyHeat - GIVING UP !... state is($data.state)"
                runIn (3, calibrateStateMachine, [data: ["state": CALIBRATE_END, "retry": 0]])
            }
            break
        case CALIBRATE_END :   // verify if back to heat (auto)
            if (device.currentValue("thermostatMode") == "heat" ) {    // TRV has been successfuly turned to heat/auto
                log.info "data.state ($data.state) ->  finished successfuly"
            }
            else {
                log.error "ERROR CALIBRATE_END - GIVING UP!... state is ($data.state)"
            }
            unschedule(calibrateStateMachine)
            break
        default :
            log.error "state calibrate UNKNOWN = ${data.state}"
            unschedule(calibrateStateMachine)
            break
    }
}

void updated() {
	def cmds = []
	cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:1,	size:1,	scaledConfigurationValue: parameter1 ? 0x01 : 0x00)
	if (lg) log.info englishLang==true ? "Parameter 1 will be set to ${parameter1 ? 0x01 : 0x00}" : "Parameter 1 hat den Wert ${parameter1 ? 0x01 : 0x00} übermittelt bekommen"
	cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:2,	size:1,	scaledConfigurationValue: Math.round(parameter2.toFloat()).toInteger())
	if (lg) log.info englishLang==true ? "Parameter 2 will be set to ${Math.round(parameter2.toFloat()).toInteger()}" : "Parameter 2 hat den Wert ${Math.round(parameter2.toFloat()).toInteger()} übermittelt bekommen"
	cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:3,	size:1,	scaledConfigurationValue: parameter3 ? 0x01 : 0x00)
	if (lg) log.info englishLang==true ? "Parameter 3 will be set to ${parameter3 ? 0x01 : 0x00}" : "Parameter 3 hat den Wert ${parameter3 ? 0x01 : 0x00} übermittelt bekommen"
	cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:4,	size:1,	scaledConfigurationValue: parameter4.toInteger())
	if (lg) log.info englishLang==true ? "Parameter 4 will be set to ${parameter4.toInteger()}" : "Parameter 4 hat den Wert ${parameter4.toInteger()} übermittelt bekommen"
	cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:5,	size:1,	scaledConfigurationValue: Math.round(parameter5.toFloat() * 10).toInteger())
	if (lg) log.info englishLang==true ? "Parameter 5 will be set to ${Math.round(parameter5.toFloat() * 10).toInteger()}" : "Parameter 5 hat den Wert ${Math.round(parameter5.toFloat() * 10).toInteger()} übermittelt bekommen"
	cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:6,	size:1,	scaledConfigurationValue: Math.round(parameter6.toFloat()).toInteger())
	if (lg) log.info englishLang==true ? "Parameter 6 will be set to ${Math.round(parameter6.toFloat()).toInteger()}" : "Parameter 6 hat den Wert ${Math.round(parameter6.toFloat()).toInteger()} übermittelt bekommen"
	cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:7,	size:1,	scaledConfigurationValue: parameter7.toInteger())
	if (lg) log.info englishLang==true ? "Parameter 7 will be set to ${parameter7.toInteger()}" : "Parameter 7 hat den Wert ${parameter7.toInteger()} übermittelt bekommen"
	if (parameter9) {
		cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:8,	size:1,	scaledConfigurationValue: -128)
		if (lg) log.info englishLang==true ? "Parameter 8 will be set to -128" : "Parameter 8 hat den Wert -128 übermittelt bekommen"
	} else {
		cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber:8,	size:1,	scaledConfigurationValue: Math.round(parameter8.toFloat() * 10).toInteger())
		if (lg) log.info englishLang==true ? "Parameter 8 will be set to ${Math.round(parameter8.toFloat() * 10).toInteger()}" : "Parameter 8 hat den Wert ${Math.round(parameter8.toFloat() * 10).toInteger()} übermittelt bekommen"
	}
	for (int i=1 ; i<=8 ; i++) {
		cmds << new hubitat.zwave.commands.configurationv1.ConfigurationGet(parameterNumber: i)
	}
	sendToDevice(cmds)
    autoRefresh()
}

void installed() {
	sendEvent(name:"Notifity",						value:"Installed", displayed: true)
	sendEvent(name:"DeviceResetLocally",			value:false, displayed: true)
	sendEvent(name:"supportedThermostatFanModes", 	value: ["circulate"], displayed: true)
	sendEvent(name:"supportedThermostatModes",		value: ["off", "heat", "emergency heat", "cool", "manual"], displayed: true)
	def cmds = []
	cmds << new hubitat.zwave.commands.protectionv1.ProtectionGet()
	cmds << new hubitat.zwave.commands.thermostatsetpointv3.ThermostatSetpointGet(setpointType:0x01)
	cmds << new hubitat.zwave.commands.thermostatsetpointv3.ThermostatSetpointGet(setpointType:0x0B)
	cmds << new hubitat.zwave.commands.batteryv1.BatteryGet()
	cmds << new hubitat.zwave.commands.thermostatmodev3.ThermostatModeGet()
	cmds << new hubitat.zwave.commands.sensormultilevelv5.SensorMultilevelGet(sensorType:1)
	for (int i=1 ; i<=8 ; i++) {
		cmds << new hubitat.zwave.commands.configurationv1.ConfigurationSet(parameterNumber: i, defaultValue: true)
	}
	sendToDevice(cmds)
    autoRefresh()
    unschedule(calibrateStateMachine)
}

def setDeviceLimits() { // for google and amazon compatability
    sendEvent(name:"minHeatingSetpoint", value: settings.tempMin ?: 8, unit: "°C", isStateChange: true, displayed: false)
	sendEvent(name:"maxHeatingSetpoint", value: settings.tempMax ?: 28, unit: "°C", isStateChange: true, displayed: false)
	log.trace "setDeviceLimits - device max/min set"
}	


def initialize() {
    log.info "initialize..."
    setDeviceLimits()
    installed()
}


void sendToDevice(List<hubitat.zwave.Command> cmds, Long delay=1000) {
	sendHubCommand(new hubitat.device.HubMultiAction(commands(cmds, delay), hubitat.device.Protocol.ZWAVE))
}

void sendToDevice(hubitat.zwave.Command cmd, Long delay=1000) {
	sendHubCommand(new hubitat.device.HubAction(zwaveSecureEncap(cmd.format()), hubitat.device.Protocol.ZWAVE))
}

List<String> commands(List<hubitat.zwave.Command> cmds, Long delay=1000) {
	return delayBetween(cmds.collect{ zwaveSecureEncap(it.format()) }, delay)
}

void fanOn() {
}

void auto() {
}

void fanAuto() {
}

void fanCirculate() {	
}

void setThermostatFanMode(fanmode) {
	sendEvent(name: "thermostatFanMode", value: "circulate", displayed: true)
}

void SendTemperature(temperature) {
	def Integer x = Math.round(temperature * 100) % 256
	def Integer y = (Math.round(temperature * 100) - x) / 256	
	sendToDevice(new hubitat.zwave.commands.sensormultilevelv10.SensorMultilevelReport(precision:2,scale:0,sensorType:1,sensorValue:[y,x],size:2,scaledSensorValue:(Math.round(temperature * 100)/100).toBigDecimal()))
}
