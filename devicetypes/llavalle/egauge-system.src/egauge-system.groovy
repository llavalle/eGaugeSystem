
preferences 
{
   input("uri", "text", title: "eGauge Monitor URL");
}

metadata 
{
   definition (name: "eGauge System", namespace: "llavalle", author: "Philippe Marseille") 
   {
      capability "Power Meter";
      capability "Refresh";
      capability "Polling";
        
      attribute "energy_today", "STRING";
        
      fingerprint deviceId: "eGauge";
   }

   simulator 
   {
      // TODO: define status and reply messages here
   }

   tiles(scale:2)   
   {
      /*multiAttributeTile(name:"totalbig", type: "generic", width: 6, height: 4)
      {
         tileAttribute ("device.power", key: "PRIMARY_CONTROL") 
         {
            attributeState "power", label:'${currentValue}W',unit:"W", backgroundColor:"#0000CC"
         }
      }*/
            
      valueTile("evcharging", "device.EVCharging", width: 2, height: 2) 
      {
         state("EVChargingPower", label: '${currentValue}W\nEVCharging', unit:"W", backgroundColor: "#0000CC");
      }
            
      valueTile("maingrid", "device.MainGridPower", width: 2, height: 2) 
      {
         state("MainGridPower", label: '${currentValue}W\nMainGrid', unit:"W", backgroundColor: "#0000CC");
      }

      valueTile("bachgrid", "device.BachGridPower", width: 2, height: 2) 
      {
         state("BachGridPower", label: '${currentValue}W\nBachGrid', unit:"W", backgroundColor: "#0000CC");
      }    
      valueTile("last24HkWh", "device.Last24HkWh", width: 2, height: 2) 
      {
         state("Last24HkWh", label: '${currentValue}kWh\nLast24h', unit:"kWh", backgroundColor: "#0000CC");
      }   

      standardTile("refresh", "device.refresh", decoration: "flat", width: 2, height: 2) 
      {
         state "default", action:"polling.poll", icon:"st.secondary.refresh";
      }

      //main (["totalbig"]);
      //details(["totalbig","evcharging","maingrid", "bachgrid", "refresh"]);
      main (["evcharging"]);
      details(["evcharging","maingrid", "bachgrid", "last24HkWh", "refresh"]);

   }
}

def poll() 
{
   refresh()
}

def refresh() 
{
   energyRefresh()
}

def processResponse(csv){
    def snapshot = [:]

    def lineSplit = csv.split("\r\n") 

    def headers = lineSplit[0].split(',')
    def lines = lineSplit[1].split(',')

	log.debug lineSplit[0]
    log.debug lineSplit[1]
    
    def i = 0
    headers.each{
        snapshot[it - "\"" - " [Vs]\"" - " [kWh]\""] = (Double.parseDouble(lines[i]) * 3600 * 1000).toLong()
        i++
    }

    snapshot.ts = Long.parseLong(lines[0])

    return snapshot
}

def takeSnapshot(snapUri){   
    def retVal
    
    httpGet(snapUri){resp ->
        
        if (resp.data) {
            retVal = processResponse(resp.data.text);
        }

        if(resp.status == 200) {
            log.debug "poll results returned"
        }
        else{
            log.error "polling children & got http status ${resp.status}"
        }
     }
     return retVal
}

def calcAvgPower(Long oldTS, Long newTS, Long oldVal, Long newVal){
    return calcDiffPower(oldVal,newVal).intdiv(newTS-oldTS)
}

def calcDiffPower(Long oldVal, Long newVal){
    Long diffPower = 0L

    if(newVal < 0 && oldVal > 0){
        diffPower += (newVal - Long.MIN_VALUE)
        diffPower += (Long.MAX_VALUE - oldVal)
    }
    else{
        diffPower = newVal - oldVal
    }
    return diffPower
}

def energyRefresh() {
    log.debug "Executing 'energyRefresh'";


    log.debug "Poking eGauge for 15s avg"
    def newSnapSeconds = takeSnapshot("${settings.uri}/cgi-bin/egauge-show?S&a&n=1&c")
    def previousTSSeconds = newSnapSeconds.ts - 15
    def oldSnapSeconds = takeSnapshot("${settings.uri}/cgi-bin/egauge-show?S&a&n=1&c&f=${previousTSSeconds}")
    log.debug "Done"


    def avg15sPower = calcAvgPower(oldSnapSeconds.ts, newSnapSeconds.ts, oldSnapSeconds.Usage,newSnapSeconds.Usage)
    def avg15sPowerGrid = calcAvgPower(oldSnapSeconds.ts, newSnapSeconds.ts, oldSnapSeconds["Grid+"],newSnapSeconds["Grid+"])
    def avg15sPowerBach = calcAvgPower(oldSnapSeconds.ts, newSnapSeconds.ts, oldSnapSeconds["Bach Grid"],newSnapSeconds["Bach Grid"])
    log.debug(avg15sPower + "W")
    log.debug(avg15sPowerGrid + "W")
    log.debug(avg15sPowerBach + "W")

    log.debug "Poking eGauge for last 24h"
    def newSnapMinutes = takeSnapshot("${settings.uri}/cgi-bin/egauge-show?m&a&c&n=1")
    def previousTSMinutes = newSnapMinutes.ts - (24*60*60)
    def oldSnapMinutes = takeSnapshot("${settings.uri}/cgi-bin/egauge-show?m&a&c&n=1&f=${previousTSMinutes}")
    log.debug "Done"

    def tot24hpower = calcDiffPower(oldSnapMinutes.Usage,newSnapMinutes.Usage)
    
    log.debug "power: " + avg15sPower
    log.debug "MainGridPower: " + avg15sPowerGrid
    log.debug "BachGridPower: " + avg15sPowerGrid
    log.debug "Last24HkWh: " + Math.round(tot24hpower/1000/60/60)
    
    delayBetween([sendEvent(name: 'power', value: avg15sPower)
                 ,sendEvent(name: 'MainGridPower', value: avg15sPowerGrid)
                 ,sendEvent(name: 'BachGridPower', value: avg15sPowerBach)
                 ,sendEvent(name: 'Last24HkWh', value: Math.round(tot24hpower/1000/60/60))
                 ]
                )
}


