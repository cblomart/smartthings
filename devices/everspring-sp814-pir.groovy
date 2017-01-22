 metadata {
	definition (name: "Eversrping SP814 PIR", namespace: "cblomart", author: "Cédric Blomart") {
		capability 	"Motion Sensor"
		capability 	"Configuration"
		capability 	"Sensor"
		capability 	"Battery"
	}

	tiles {
		standardTile("motion", "device.motion", width: 2, height: 2) {
			state "active", label:'motion', icon:"st.motion.motion.active", backgroundColor:"#53a7c0"
			state "inactive", label:'no motion', icon:"st.motion.motion.inactive", backgroundColor:"#ffffff"
		}
		valueTile("illuminance", "device.illuminance", inactiveLabel: false) {
			state "luminosity", label:'${currentValue} ${unit}', unit:"lux"
		}
		valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat") {
			state "battery", label:'${currentValue}% battery', unit:""
		}
		standardTile("configure", "device.configure", inactiveLabel: false, decoration: "flat") {
			state "configure", label:'', action:"configuration.configure", icon:"st.secondary.configure"
		}

		main(["motion", "illuminance"])
		details(["motion", "battery", "illuminance", "configure"])
	}
}

def configure() {

	log.debug "Configuring Device For SmartThings Use"
    def cmds = []
    
    // send basic set to hub
    cmds << zwave.associationV2.associationSet(groupingIdentifier:2, nodeId:[zwaveHubNodeId]).format()

	// Sensor sensitivity
	cmds << zwave.configurationV1.configurationSet(configurationValue: [10], parameterNumber: 3, size: 1).format()
    cmds << zwave.configurationV1.configurationGet(parameterNumber: 3).format()
    
	// get battry status and get back to sleep
    cmds << zwave.batteryV1.batteryGet().format()
    cmds << "delay 1200"
    cmds << zwave.wakeUpV1.wakeUpNoMoreInformation().format()

	delayBetween(cmds, 500)
}

def parse(String description)
{
	def result = []
	def cmd = zwave.parse(description, [0x20: 1, 0x80: 1, 0x86: 1, 0x84: 2, 0x85: 2, 0x70: 1, 0x30: 1, 0x72: 1])
    
    if (description == "updated") {
        result << response(zwave.wakeUpV2.wakeUpIntervalSet(seconds: 7200, nodeid:zwaveHubNodeId))
		result << response(zwave.manufacturerSpecificV1.manufacturerSpecificGet())            
	}
    
	if (cmd) {
		if( cmd.CMD == "8407" ) { 
            result << response(zwave.batteryV1.batteryGet().format())
        	result << new physicalgraph.device.HubAction(zwave.wakeUpV1.wakeUpNoMoreInformation().format()) 
        }
		result << createEvent(zwaveEvent(cmd))
	}
    
    if ( result[0] != null ) {
		log.debug "Parse returned ${result}"
		result
    }
}

//create event

def createEvent(physicalgraph.zwave.commands.versionv1.VersionReport cmd, Map item1) {	
    updateDataValue("applicationVersion", "${cmd.applicationVersion}")
    log.debug "applicationVersion:      ${cmd.applicationVersion}"
    log.debug "applicationSubVersion:   ${cmd.applicationSubVersion}"
    log.debug "zWaveLibraryType:        ${cmd.zWaveLibraryType}"
    log.debug "zWaveProtocolVersion:    ${cmd.zWaveProtocolVersion}"
    log.debug "zWaveProtocolSubVersion: ${cmd.zWaveProtocolSubVersion}"
}

// Event Generation

// Basic Set
def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd) {
	def map = [:]
	map.value = cmd.value ? "active" : "inactive"
	map.name = "motion"
	if (map.value == "active") {
		map.descriptionText = "$device.displayName detected motion"
	}
	else {
		map.descriptionText = "$device.displayName motion has stopped"
	}
	map
}

// Battery Report
def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
	def map = [:]
	map.name = "battery"
	map.value = cmd.batteryLevel > 0 ? cmd.batteryLevel.toString() : 1
	map.unit = "%"
	map.displayed = false
	map
}

// Wake up
def zwaveEvent(physicalgraph.zwave.commands.wakeupv2.WakeUpNotification cmd)
{
	[descriptionText: "${device.displayName} woke up", isStateChange: false]
}

// Configuration 
def zwaveEvent(physicalgraph.zwave.commands.configurationv1.ConfigurationReport cmd) {
	log.debug "${device.displayName} parameter '${cmd.parameterNumber}' with a byte size of '${cmd.size}' is set to '${cmd.configurationValue}'"
}

// Sensor Binary
def zwaveEvent(physicalgraph.zwave.commands.sensorbinaryv1.SensorBinaryReport cmd) {
	def map = [:]
	map.value = cmd.sensorValue ? "active" : "inactive"
	map.name = "motion"
	if (map.value == "active") {
		map.descriptionText = "$device.displayName detected motion"
	}
	else {
		map.descriptionText = "$device.displayName motion has stopped"
	}
	map
}

// Manufacturer Specific
def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv1.ManufacturerSpecificReport cmd) {
	def result = []

	def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
	log.debug "msr: $msr"
    updateDataValue("MSR", msr)
    
    if ( msr == "010F-0800-2001" ) { //this is the msr and device type for the fibaro motion sensor
    	configure()
    }

	result << createEvent(descriptionText: "$device.displayName MSR: $msr", isStateChange: false)
	result
}


// Catchall
def zwaveEvent(physicalgraph.zwave.Command cmd) {
	log.debug "Catchall reached for cmd: ${cmd.toString()}}"
	[:]
}



