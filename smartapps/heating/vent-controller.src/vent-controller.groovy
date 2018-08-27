/**
 *  Copyright 2015 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Virtual Thermostat
 *
 *  Author: Jeff Ernst
 */
definition(
    name: "Vent Controller",
    namespace: "Heating",
    author: "Jeff Ernst",
    description: "Control a vent in conjunction with any temperature sensor.",
    category: "Green Living",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/temp_thermo-switch.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/temp_thermo-switch@2x.png"
)

preferences {
	section("Choose a temperature sensor... ")
    {
		input "sensor", "capability.temperatureMeasurement", title: "Sensor"
	}
	section("Select the heater or air conditioner vent... ")
    {
        input "ventSwitch", "capability.switch", title: "Heater Vent switch in room", required: false, description: "Optional"
	}
	section("Set the desired heat temperature...")
    {
		input "heatSetpoint", "decimal", title: "Set Temp", required: true
	}
    section("Set the desired cool temperature...")
    {
		input "coolSetpoint", "decimal", title: "Set Temp", required: true
	}
    section("Turn on between what times?")
    {
        input "fromTime", "time", title: "From", required: true
        input "toTime", "time", title: "To", required: true
    }
    section("Only when mode is...")
    {
    	input "modes", "mode", title: "Modes", multiple: true, required: true
    }
    section("Via a push notification and/or an SMS message")
    {
        input("recipients", "contact", title: "Send notifications to") 
        {
            input "phone", "phone", title: "Enter a phone number to get SMS", required: false
            paragraph "If outside the US please make sure to enter the proper country code"
            input "sendPushNotification", "enum", title: "Notify me via Push Notification", required: false, options: ["Yes", "No"]
        }
    }
}

def installed()
{
	initialize()
}

def updated()
{
	unsubscribe()
    unschedule()
	initialize()
}

def initialize()
{
	state.thermostatMode = "Heat"
	subscribe(sensor, "temperature", temperatureHandler)
    subscribe(ventSwitch, "temperature", ventTemperatureHandler)
    schedule(fromTime, startTimeHandler)
    schedule(toTime, endTimeHandler)
}

def startTimeHandler(evt)
{
	def currentState = sensor.currentState("temperature")
    evaluate(currentState.doubleValue, evt)
    
    def smartAppLabel = app.getLabel()
	sendMessage(evt, "Start time reached for SmartApp $smartAppLabel")
}

def endTimeHandler(evt)
{
    ventSwitch.setLevel(100)
    
    def smartAppLabel = app.getLabel()
    sendMessage(evt, "End time reached for SmartApp $smartAppLabel")
}

def ventTemperatureHandler(evt)
{
	def ventTemperature = evt.doubleValue
	log.debug "The temperature at the vent is ${ventTemperature}"
    log.debug "Current thermostat mode is ${state.thermostatMode}"
    
    if(ventTemperature > 30.0 && state.thermostatMode != "Heat")
    {
    	state.thermostatMode = "Heat"
        log.debug "Setting thermostat mode to Heat"
    }
    else if(ventTemperature < 15.0 && state.thermostatMode != "Cool")
    { 
    	state.thermostatMode = "Cool"
        log.debug "Setting thermostat mode to Cool"
    }
    
    def currentState = sensor.currentState("temperature")
    evaluate(currentState.doubleValue, evt)
}

def temperatureHandler(evt)
{
    // get the event name, e.g., "switch"
    log.debug "This event name is ${evt.name}"

    // get the value of this event, e.g., "on" or "off"
    log.debug "The value of this event is ${evt.value}"

    // get the Date this event happened at
    log.debug "This event happened at ${evt.date}"

    // did the value of this event change from its previous state?
    log.debug "The value of this event is different from its previous value: ${evt.isStateChange()}"
    evaluate(evt.doubleValue, evt)
}

private evaluate(currentTemp, evt)
{
    def smartAppLabel = app.getLabel()
    if(!(location.mode in modes))
    {
    	log.debug "The current mode $location.mode is not in selected modes for SmartApp $smartAppLabel, exiting..."
        //sendMessage(evt, "The current mode $location.mode is not in selected modes for SmartApp $smartAppLabel, exiting...")
        return
    }

	def between = timeOfDayIsBetween(fromTime, toTime, new Date(), location.timeZone)
    def df = new java.text.SimpleDateFormat("EEE, MMM d yyyy HH:mm:ss")
    df.setTimeZone(location.timeZone)
        
	int currentLevel = ventSwitch.currentValue("level") 
    def switchStatus = ventSwitch.currentSwitch
    log.debug "Switch status: $switchStatus   Level: $currentLevel"
    
    if (between)
    {        
        if(state.thermostatMode == "Heat")
        {
        	log.debug "Heat EVALUATE($currentTemp, $heatSetpoint)"
            if (currentTemp < heatSetpoint)
            {
                if(currentLevel < 100 || switchStatus == "off")
                {
                    def currentTime = df.format(new Date())
                    def msg = "SmartApp $smartAppLabel opened vent on $currentTime with currentTemp of $currentTemp C"
                    sendMessage(evt, msg)
                    ventSwitch.setLevel(100)
                    ventSwitch.on()
                }
            }
            else
            {
                if(currentLevel > 0 || switchStatus == "on")
                {
                    def currentTime = df.format(new Date())
                    def msg = "SmartApp $smartAppLabel closed vent on $currentTime with currentTemp of $currentTemp C"
                    sendMessage(evt, msg)
                    ventSwitch.setLevel(0)
                    ventSwitch.off()
                }
            }
        }
        else
        {
            log.debug "Cool EVALUATE($currentTemp, $coolSetpoint)"
            if (currentTemp >= coolSetpoint)
            {
                if(currentLevel < 100 || switchStatus == "off")
                {
                    def currentTime = df.format(new Date())
                    def msg = "SmartApp $smartAppLabel opened vent on $currentTime with currentTemp of $currentTemp C"
                    sendMessage(evt, msg)
                    ventSwitch.setLevel(100)
                    ventSwitch.on()
                }
            }
            else
            {
                if(currentLevel > 0 || switchStatus == "on")
                {
                    def currentTime = df.format(new Date())
                    def msg = "SmartApp $smartAppLabel closed vent on $currentTime with currentTemp of $currentTemp C"
                    sendMessage(evt, msg)
                    ventSwitch.setLevel(0)
                    ventSwitch.off()
                }
            }
        }
    }
    else
    {
        if(currentLevel < 100 || switchStatus == "off")
        {
            def currentTime = df.format(new Date())
            def msg = "SmartApp $smartAppLabel opened vent on $currentTime since it is outside of the valid time"
            sendMessage(evt, msg)
            ventSwitch.setLevel(100)
            ventSwitch.on()
        }
    }
    
}

private sendMessage(evt, msg) 
{
	Map options = [:]
    options = [translatable: true, triggerEvent: evt]
    
	log.debug "sendPushNotification:$sendPushNotification, '$msg'"

	if (location.contactBookEnabled)
    {
		sendNotificationToContacts(msg, recipients, options)
	}
    else
    {
		if (phone)
        {
			options.phone = phone
			if (sendPushNotification == 'Yes')
            {
				log.debug 'Sending push and SMS'
				options.method = 'both'
			} 
            else
            {
				log.debug 'Sending SMS'
				options.method = 'phone'
			}
		}
        else if (sendPushNotification == 'Yes')
        {
			log.debug 'Sending push'
			options.method = 'push'
		}
        else
        {
			log.debug 'Sending nothing'
			options.method = 'none'
		}
		sendNotification(msg, options)
	}
}