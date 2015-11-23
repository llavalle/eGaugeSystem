/**
 *  eGauge Energy Monitoring System
 * 
<measurements serial="0x0123456789">
  <timestamp>1447853826</timestamp>
  <cpower src="Grid" i="3" u="1">360.6</cpower>
  <cpower src="Grid" i="2" u="0">595.6</cpower>
    [...]
  <meter title="Grid">
    <energy>166.0</energy>
    <energyWs>597703450</energyWs>
    <power>956.2</power>
  </meter>
  <frequency>59.99</frequency>
  <voltage ch="0">121.34</voltage>
  <voltage ch="1">121.88</voltage>
    [...]
  <current ch="15">0.013</current>
</measurements>
 
 */
 
preferences 
{
   input("uri", "text", title: "eGauge Monitor URL");
   input("uri2", "text", title: "eGauge2 Monitor URL");
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

      standardTile("refresh", "device.refresh", decoration: "flat", width: 2, height: 2) 
      {
         state "default", action:"polling.poll", icon:"st.secondary.refresh";
      }

      //main (["totalbig"]);
      //details(["totalbig","evcharging","maingrid", "bachgrid", "refresh"]);
      main (["evcharging"]);
      details(["evcharging","maingrid", "bachgrid", "refresh"]);

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

def processResponse(xml){
    def snapshot = [:]


    def i = 0
    xml.data.cname.each{
        snapshot[it.text().toString()] = xml.data.r.c[i].text().toLong()
        i++
            }

    snapshot.ts = Long.parseLong(xml.data.@time_stamp.text()[2..-1],16)

    return snapshot
}

def takeSnapshot(snapUri){
    log.debug "taking snapshot"
    log.debug (snapUri)
    
    httpGet(snapUri){resp ->
        
        if (resp.data) {
            return processResponse(resp.data);
        }

        if(resp.status == 200) {
            log.debug "poll results returned"
        }
        else{
            log.error "polling children & got http status ${resp.status}"
        }
     }
     
     log.debug "snapshot taken"
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
    def newSnapSeconds = takeSnapshot("${settings.uri}/cgi-bin/egauge-show?S&a&n=1")
    def previousTSSeconds = newSnapSeconds.ts - 15
    def oldSnapSeconds = takeSnapshot("${settings.uri}/cgi-bin/egauge-show?S&a&n=1&f=${previousTSSeconds}")
    log.debug "Done"

    def avg15sPowerTotal = calcAvgPower(oldSnapSeconds.ts, newSnapSeconds.ts, oldSnapSeconds.use,newSnapSeconds.use)
    def avg15sPowerMain = calcAvgPower(oldSnapSeconds.ts, newSnapSeconds.ts, oldSnapSeconds['Grid+'],newSnapSeconds['Grid+'])
    def avg15sPowerBach = calcAvgPower(oldSnapSeconds.ts, newSnapSeconds.ts, oldSnapSeconds['Bach Grid'],newSnapSeconds['Bach Grid'])
    log.debug(avg15sPower + "W")

    log.debug "Poking eGauge for last 24h"
    def newSnapMinutes = takeSnapshot("${settings.uri}/cgi-bin/egauge-show?m&a&n=1")
    def previousTSMinutes = newSnapMinutes.ts - (24*60*60)
    def oldSnapMinutes = takeSnapshot("${settings.uri}/cgi-bin/egauge-show?m&a&n=1&f=${previousTSMinutes}")
    log.debug "Done"

    def tot24hpower = calcDiffPower(oldSnapMinutes.use,newSnapMinutes.use)
    log.debug Math.round(tot24hpower/1000/60/60)
    
    delayBetween([sendEvent(name: 'power', value: avg15sPowerTotal)
                 ,sendEvent(name: 'MainGridPower', value: avg15sPowerMain)
                 ,sendEvent(name: 'BachGridPower', value: avg15sPowerBach)
                 ]
                )
}
