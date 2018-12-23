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
    name: "Virtual Thermostat",
    namespace: "Heating",
    author: "Jeff Ernst",
    description: "Control a space heater in conjunction with any temperature sensor.",
    category: "Green Living",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Meta/temp_thermo-switch.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Meta/temp_thermo-switch@2x.png"
)

preferences {
	section("Choose a temperature sensor... ")
    {
		input "sensor", "capability.temperatureMeasurement", title: "Sensor"
	}
	section("Select the heater or air conditioner outlet(s)... ")
    {
		input "outlets", "capability.switch", title: "Outlets", multiple: true
	}
	section("Set the desired temperature...")
    {
		input "setpoint", "decimal", title: "Set Temp", required: true
	}
    section("Turn on between what times?")
    {
        input "fromTime", "time", title: "From", required: true
        input "toTime", "time", title: "To", required: true
    }
    section("Only if this override switch is off")
    {
    	input "disableSwitch", "capability.switch", title: "Choose the disable switch", multiple: false, required: false
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
	subscribe(sensor, "temperature", temperatureHandler)
    schedule(fromTime, startTimeHandler)
    schedule(toTime, endTimeHandler)
}

def startTimeHandler(evt)
{
    def smartAppLabel = app.getLabel()
	def currentState = sensor.currentState("temperature")
    evaluate(currentState.doubleValue, evt)
        
	sendMessage(evt, "Start time reached for SmartApp $smartAppLabel")
}

def endTimeHandler(evt)
{
    outlets.off()
    
    def smartAppLabel = app.getLabel()
    sendMessage(evt, "End time reached for SmartApp $smartAppLabel")
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
    
    if(disableSwitch)
    {
    	def overrideSwitchValue = disableSwitch.currentSwitch
        if(overrideSwitchValue == "on")
        {
        	log.debug "The disable switch is $overrideSwitchValue for SmartApp $smartAppLabel, exiting..."
        	return
        }
        else
        {
        	log.debug "The disable switch is $overrideSwitchValue for SmartApp $smartAppLabel, continuing to process..."
        }        
    }
    else
    {
    	log.debug "No override switch has been specified"
    }    

	def between = timeOfDayIsBetween(fromTime, toTime, new Date(), location.timeZone)
    def df = new java.text.SimpleDateFormat("EEE, MMM d yyyy HH:mm:ss")
    df.setTimeZone(location.timeZone)
        
    def currSwitches = outlets.currentSwitch
    def offSwitches = currSwitches.findAll
    { switchVal ->
        switchVal == "off" ? true : false
    }
    def onSwitches = currSwitches.findAll
    { switchVal ->
        switchVal == "on" ? true : false
    }
    
    if (between)
    {
        log.debug "EVALUATE($currentTemp, $setpoint)"
        if (currentTemp < setpoint)
        {
            if(offSwitches.size() > 0)
            {
            	def currentTime = df.format(new Date())
    			def msg = "SmartApp $smartAppLabel turned on heater(s) on $currentTime with currentTemp of $currentTemp C"
                sendMessage(evt, msg)
                outlets.on()
            }
        }
        else
        {
            if(onSwitches.size() > 0)
            {
                def currentTime = df.format(new Date())
                def msg = "SmartApp $smartAppLabel turned off heater(s) on $currentTime with currentTemp of $currentTemp C"
                sendMessage(evt, msg)
                outlets.off()
            }
        }
    }
    else
    {
        if(onSwitches.size() > 0)
        {
            def currentTime = df.format(new Date())
            def msg = "SmartApp $smartAppLabel turned off heater(s) on $currentTime since it is outside of the valid time"
            sendMessage(evt, msg)
            outlets.off()
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