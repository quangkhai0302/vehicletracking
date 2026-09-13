// Browser-only API fixtures. No Spring, Docker, provider, or user's database access.
import http from 'node:http';
import { randomUUID } from 'node:crypto';
const polyline = points => {
  const alphabet='ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';
  const encode=value=>{let text='';while(value>=32){text+=alphabet[(value&31)|32];value>>>=5;}return text+alphabet[value];};
  let previous=[0,0];
  return 'BF'+points.map(point=>point.map((value,i)=>{const next=Math.round(value*1e5),delta=next-previous[i];previous[i]=next;return encode(delta<0?-delta*2-1:delta*2);}).join('')).join('');
};
export async function startFixtureServer() {
  const stamp=new Date().toISOString(),clients=new Set();
  const stations=[{id:1,name:'A · SIMULATOR',latitude:10.77,longitude:106.7},{id:2,name:'B · SIMULATOR',latitude:10.771,longitude:106.701}]
    .map(item=>({...item,address:null,checkinRadiusMeters:50,active:true,createdAt:stamp,updatedAt:stamp}));
  const route={id:1,name:'SIMULATOR · Tuyến vòng A-B-A',transportMode:'CAR',routingProvider:'HERE',totalDistanceMeters:312,
    estimatedTravelDurationSeconds:40,baseTravelDurationSeconds:40,totalDwellDurationSeconds:4,estimatedTripDurationSeconds:44,
    estimatedDepartureAt:stamp,calculatedAt:stamp,createdAt:stamp,
    stops:[stations[0],stations[1],stations[0]].map((s,i)=>({sequenceNumber:i+1,stationId:s.id,stationName:s.name,latitude:s.latitude,longitude:s.longitude,
      role:i===0?'START':i===2?'END':'STOP',dwellDurationSeconds:i===1?4:0,distanceFromPreviousMeters:i?156:0,
      travelDurationFromPreviousSeconds:i?20:0,arrivalOffsetSeconds:[0,20,44][i],departureOffsetSeconds:[0,24,44][i]})),
    sections:[{sectionSequence:1,destinationStopSequence:2,encodedPolyline:polyline([[10.77,106.7],[10.771,106.701]]),distanceMeters:156,travelDurationSeconds:20,baseTravelDurationSeconds:20},
      {sectionSequence:2,destinationStopSequence:3,encodedPolyline:polyline([[10.771,106.701],[10.77,106.7]]),distanceMeters:156,travelDurationSeconds:20,baseTravelDurationSeconds:20}]};
  const vehicle={id:1,plateNumber:'FIX006',name:'Xe fixture 006',description:null,active:true,createdAt:stamp,updatedAt:stamp};
  let sequence=0,position=null;
  const trips=[],runs=[];
  const addTrip=()=>{const time=new Date().toISOString();const trip={id:++sequence,vehicleId:1,vehiclePlateNumber:vehicle.plateNumber,routeId:1,routeName:route.name,status:'SCHEDULED',
    scheduledDepartureAt:time,plannedEndAt:new Date(Date.parse(time)+44000).toISOString(),startedAt:null,endedAt:null,createdAt:time};trips.push(trip);return trip;};
  addTrip();
  function frame(elapsed,running=true) {
    const fraction=elapsed<20?elapsed/20:elapsed<24?1:1-(elapsed-24)/20;
    return {latitude:10.77+.001*fraction,longitude:106.7+.001*fraction,heading:elapsed<24?45:225,
      speedKmh:running && !(elapsed>=20&&elapsed<24)&&elapsed<44?28.06:0,progressPercent:elapsed<=20?elapsed/20*50:elapsed<24?50:50+(elapsed-24)/20*50,
      nextStopSequence:elapsed<20?2:3,nextStopEtaSeconds:elapsed<20?20-elapsed:44-elapsed,dwelling:elapsed>=20&&elapsed<24,finished:elapsed>=44};
  }
  const snapshot=()=>({serverTime:new Date().toISOString(),positions:position?[position]:[],simulations:runs,trips});
  function update(run) {
    const trip=trips.find(t=>t.id===run.tripId),now=new Date().toISOString();
    run.frame=frame(run.elapsedSeconds,run.status==='RUNNING');run.updatedAt=now;run.simulatedAt=new Date(Date.parse(trip.scheduledDepartureAt)+run.elapsedSeconds*1000).toISOString();
    position={id:Date.now(),eventId:randomUUID(),tripId:trip.id,vehicleId:trip.vehicleId,recordedAt:now,receivedAt:now,simulatedAt:run.simulatedAt,
      latitude:run.frame.latitude,longitude:run.frame.longitude,heading:run.frame.heading,speedKmh:run.frame.speedKmh,accuracyMeters:0,source:'SIMULATOR'};
  }
  const push=()=>{const value=snapshot();for(const res of clients)res.write(`event: snapshot\nid: ${value.serverTime}\nretry: 1000\ndata: ${JSON.stringify(value)}\n\n`);};
  const tick=setInterval(()=>{for(const run of runs)if(run.status==='RUNNING'){
    run.elapsedSeconds=Math.min(44,run.elapsedSeconds+.25*run.multiplier);
    if(run.elapsedSeconds===44){run.status='COMPLETED';const trip=trips.find(t=>t.id===run.tripId);trip.status='COMPLETED';trip.endedAt=new Date().toISOString();}
    update(run);
  }push();},250);
  const writes=[];
  let commandError=false,holdStream=false;
  const server=http.createServer(async(req,res)=>{
    res.setHeader('Access-Control-Allow-Origin','http://127.0.0.1:5173');res.setHeader('Access-Control-Allow-Headers','Content-Type,Last-Event-ID');res.setHeader('Access-Control-Allow-Methods','GET,POST,PUT,DELETE,OPTIONS');
    if(req.method==='OPTIONS'){res.writeHead(204);res.end();return;}
    const path=new URL(req.url,'http://fixture').pathname;
    if(path.endsWith('/telemetry/stream') && holdStream){res.writeHead(503);res.end();return;}
    if(path.endsWith('/telemetry/stream')){res.writeHead(200,{'Content-Type':'text/event-stream','Cache-Control':'no-store'});clients.add(res);push();req.on('close',()=>clients.delete(res));return;}
    const json=(data,status=200)=>{res.writeHead(status,{'Content-Type':'application/json'});res.end(JSON.stringify(data));};
    let raw='';for await(const chunk of req)raw+=chunk;
    if(req.method!=='GET')writes.push({path,body:raw});
    if(path.endsWith('/telemetry/snapshot'))return json(snapshot());
    if(path.endsWith('/stations'))return json(stations);
    if(path.endsWith('/vehicles'))return json([vehicle]);
    if(path.endsWith('/routes'))return json([{...route,stopCount:3,startStationName:'A',endStationName:'A'}]);
    if(/\/routes\/\d+$/.test(path))return json(route);
    if(path.endsWith('/trips'))return json(trips);
    const match=path.match(/\/trips\/(\d+)(?:\/simulation\/(\w+))?$/);
    if(match) {
      const trip=trips.find(t=>t.id===Number(match[1]));if(!trip)return json({detail:'Không tìm thấy chuyến.'},404);
      if(!match[2])return json({trip,route,stops:route.stops.map(stop=>({...stop,checkinRadiusMeters:50,plannedArrivalAt:new Date(Date.parse(trip.scheduledDepartureAt)+stop.arrivalOffsetSeconds*1000).toISOString(),plannedDepartureAt:new Date(Date.parse(trip.scheduledDepartureAt)+stop.departureOffsetSeconds*1000).toISOString()}))});
      const action=match[2];let run=runs.find(r=>r.tripId===trip.id);
      if(commandError)return json({detail:'Xe đang thực hiện chuyến khác (fixture).'},409);
      if(action==='play') {
        if(!run){run={id:trip.id,tripId:trip.id,status:'PAUSED',multiplier:1,elapsedSeconds:0,durationSeconds:44,errorMessage:null,replacementTripId:null,updatedAt:stamp,simulatedAt:trip.scheduledDepartureAt,frame:frame(0,false)};runs.push(run);}
        run.status='RUNNING';trip.status='IN_PROGRESS';trip.startedAt??=new Date().toISOString();
      } else if(!run)return json({detail:'Chưa có phiên.'},404);
      else if(action==='pause')run.status='PAUSED';
      else if(action==='speed')run.multiplier=JSON.parse(raw).multiplier;
      else if(action==='stop'||action==='reset'){
        run.status='STOPPED';trip.status='CANCELLED';trip.endedAt=new Date().toISOString();
        update(run);
        if(action==='reset'){
          if(run.replacementTripId)return json(runs.find(r=>r.tripId===run.replacementTripId));
          const next=addTrip();run.replacementTripId=next.id;
          const replacement={...run,id:next.id,tripId:next.id,status:'PAUSED',multiplier:1,elapsedSeconds:0,replacementTripId:null,frame:frame(0,false),simulatedAt:next.scheduledDepartureAt};
          runs.push(replacement);push();return json(replacement);
        }
      }
      update(run);push();return json(run);
    }
    return json({detail:'Unhandled fixture path: '+path},404);
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  return {api:'http://127.0.0.1:'+server.address().port+'/api/v1',tripId:1,writes,
    setCommandError:value=>{commandError=value;},snapshot,
    holdStream:value=>{holdStream=value;},
    disconnect:()=>{for(const client of clients)client.end();clients.clear();},
    stale:seconds=>{if(position)position.recordedAt=new Date(Date.now()-seconds*1000).toISOString();push();},
    gpsAge:seconds=>{
      if(!trips.some(trip=>trip.id===900))trips.push({...trips[0],id:900,status:'IN_PROGRESS'});
      position={...position,source:'GPS',simulatedAt:null,tripId:900,recordedAt:new Date(Date.now()-seconds*1000).toISOString()};push();
    },
    close:async()=>{clearInterval(tick);for(const client of clients)client.end();server.closeAllConnections();await new Promise(resolve=>server.close(resolve));}};
}
