/**
 *  eGauge Energy Monitoring System
 * 
<measurements serial="0x487512c6">
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
      multiAttributeTile(name:"totalbig", type: "generic", width: 6, height: 4)
      {
         tileAttribute ("device.power", key: "PRIMARY_CONTROL") 
         {
            attributeState "power", label:'${currentValue}W',unit:"W", 
               backgroundColors:
               [
                  [value: 0, color: "#0000CC"],
                  [value: 500, color: "#0000CC"],
                  [value: 1000, color: "#0000CC"],
                  [value: 5000, color: "#0000CC"],
                  [value: 10000, color: "#0000CC"],
                  [value: 20000, color: "#0000CC"],
                  [value: 30000, color: "#0000CC"]
               ]
         }
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

      main (["totalbig"]);
      details(["totalbig","maingrid", "bachgrid", "refresh"]);

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

def extractValue(def response, def meterName)
{
   log.debug "${response}";
   def gpathString = response.text();
   float retVal = 0.0f;
   int retEpoch = 0;

   response.meter.each 
   { 
      log.debug it.name() + ":"+ it.@title + ":" + it.text();
      if (it.name().equals("meter") && it.@title.equals(meterName)) 
      {
         retVal= Float.parseFloat(it.power.text()).trunc();
      }
   }
    
   def mapReturn = [value:retVal, epoch:retEpoch];
   return mapReturn;
}

def energyRefresh() 
{  
   log.debug "Executing 'energyRefresh'";
   
   def currentTotalGridPower = 0;
   
   def currentMainGridPower = 0;
   def currentMainGridEpoch = 0;
   
   def currentBachGridPower = 0;
   def currentBachGridEpoch = 0;
   
   log.debug "Poking first eGauge";
   httpGet("${settings.uri}/cgi-bin/egauge?noteam") 
   {resp ->
      if (resp.data) 
      {
         log.debug "${resp.data}";
         
         def mapMainGridPower = extractValue(resp.data,"Grid");
         currentMainGridPower = mapMainGridPower.value;
         currentTotalGridPower = currentTotalGridPower + currentMainGridPower;
      }
      
      if(resp.status == 200) { log.debug "poll results returned";}
      else{log.error "polling children & got http status ${resp.status}";}
   }
   
   log.debug "Poking second eGauge";
   httpGet("${settings.uri2}/cgi-bin/egauge?noteam") 
   {resp ->
      if (resp.data) 
      {
         log.debug "${resp.data}";
         
         def mapBachGridPower = extractValue(resp.data,"Grid");
          
         currentBachGridPower = mapBachGridPower.value;
         currentTotalGridPower = currentTotalGridPower + currentBachGridPower;
      }
      
      if(resp.status == 200) { log.debug "poll results returned";}
      else{log.error "polling children & got http status ${resp.status}";}
   }
    
   delayBetween([sendEvent(name: 'power', value: (currentTotalGridPower))
                ,sendEvent(name: 'MainGridPower', value: (currentMainGridPower))
                ,sendEvent(name: 'BachGridPower', value: (currentBachGridPower))
                ]
               );
}
