// Local, in-memory multi-vehicle API/SSE fixture. No provider or database writes.
import http from 'node:http';
const encode=points=>{
  const alphabet='ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_';
  const unsigned=value=>{let result='';while(value>31){result+=alphabet[(value&31)|32];value>>>=5;}return result+alphabet[value];};
  const previous=[0,0];return 'BF'+points.map(point=>point.map((value,i)=>{const next=Math.round(value*1e5),delta=next-previous[i];previous[i]=next;return unsigned(delta<0?-delta*2-1:delta*2);}).join('')).join('');
};
export async function startFleetFixture({distinctRoutes=false}={}) {
  const stamp=new Date().toISOString(),clients=new Set(),writes=[],positions=new Map(),runs=new Map();
  let failedTrip=null,streamHeld=false;
  let invalidRoute=null;
  const detailGets=[];
  const vehicles=[1,2,3].map(id=>({id,plateNumber:`FIX011${id}`,name:`Xe mô phỏng ${id}`,active:true,createdAt:stamp,updatedAt:stamp}));
  const stations=[{id:1,name:'Bến chung · fixture',latitude:10.77,longitude:106.7},{id:2,name:'Bến khác · fixture',latitude:10.8,longitude:106.72}]
    .map(item=>({...item,active:true,checkinRadiusMeters:50,createdAt:stamp,updatedAt:stamp}));
  const trips=vehicles.map(v=>({id:v.id,vehicleId:v.id,vehiclePlateNumber:v.plateNumber,routeId:v.id,routeName:`Tuyến fixture ${v.id}`,status:'SCHEDULED',
    scheduledDepartureAt:stamp,plannedEndAt:new Date(Date.parse(stamp)+600000).toISOString(),startedAt:null,endedAt:null,createdAt:stamp}));
  trips.push({...trips[0],id:4,scheduledDepartureAt:new Date(Date.parse(stamp)+3600000).toISOString()});
  const geometry=trip=>{
    const start=trip.vehicleId===3?stations[1]:stations[0];
    return [[start.latitude,start.longitude],...(distinctRoutes?[[start.latitude+.004,start.longitude+.005]]:[]),
      [start.latitude+.02,start.longitude+(distinctRoutes&&trip.vehicleId===2?-.025:.025)]];
  };
  const detail=trip=>{
    const start=trip.vehicleId===3?stations[1]:stations[0],points=geometry(trip);
    const stops=[points[0],points.at(-1)].map((point,i)=>({stationId:i?8:start.id,stationName:i?'Trạm cuối · fixture':start.name,sequenceNumber:i+1,
      latitude:point[0],longitude:point[1],role:i?'END':'START',checkinRadiusMeters:50,dwellDurationSeconds:0,arrivalOffsetSeconds:i*600,
      departureOffsetSeconds:i*600,plannedArrivalAt:trip.plannedEndAt,plannedDepartureAt:trip.plannedEndAt}));
    return {trip,stops,route:{id:trip.routeId,name:trip.routeName,transportMode:'CAR',routingProvider:'HERE',totalDistanceMeters:3600,
      estimatedTravelDurationSeconds:600,baseTravelDurationSeconds:600,totalDwellDurationSeconds:0,estimatedTripDurationSeconds:600,
      calculatedAt:stamp,createdAt:stamp,estimatedDepartureAt:stamp,stops,
      sections:[{sectionSequence:1,destinationStopSequence:2,encodedPolyline:trip.id===invalidRoute?'broken!':encode(points),distanceMeters:3600,travelDurationSeconds:600,baseTravelDurationSeconds:600}]}};
  };
  const snapshot=()=>({serverTime:new Date().toISOString(),trips,simulations:[...runs.values()],positions:[...positions.values()],checkIns:trips.map(trip=>({tripId:trip.id,revision:0,visits:[],nextStopSequence:1,awaitingExit:false})),notifications:[]});
  const push=()=>{for(const client of clients)client.write(`event: snapshot\ndata: ${JSON.stringify(snapshot())}\n\n`);};
  const update=run=>{
    const trip=trips.find(item=>item.id===run.tripId),points=geometry(trip),fraction=run.elapsedSeconds/600,now=new Date().toISOString();
    const leg=Math.min(points.length-2,Math.floor(fraction*(points.length-1))),offset=fraction*(points.length-1)-leg;
    const start=points[leg],end=points[leg+1];
    run.frame={latitude:start[0]+(end[0]-start[0])*offset,longitude:start[1]+(end[1]-start[1])*offset,heading:45,speedKmh:run.status==='RUNNING'?21.6:0,
      progressPercent:fraction*100,nextStopSequence:2,nextStopEtaSeconds:600-run.elapsedSeconds,dwelling:false,finished:run.status==='COMPLETED'};
    run.updatedAt=now;run.simulatedAt=new Date(Date.parse(trip.scheduledDepartureAt)+run.elapsedSeconds*1000).toISOString();
    positions.set(trip.vehicleId,{id:Date.now(),eventId:String(Date.now()),vehicleId:trip.vehicleId,tripId:trip.id,recordedAt:now,receivedAt:now,source:'SIMULATOR',simulatedAt:run.simulatedAt,...run.frame,accuracyMeters:0});
  };
  const timer=setInterval(()=>{for(const run of runs.values())if(run.status==='RUNNING'){
    run.elapsedSeconds=Math.min(600,run.elapsedSeconds+.25*run.multiplier);if(run.elapsedSeconds===600){run.status='COMPLETED';trips.find(t=>t.id===run.tripId).status='COMPLETED';}update(run);
  }push();},250);
  const server=http.createServer(async(req,res)=>{
    res.setHeader('Access-Control-Allow-Origin','http://127.0.0.1:5173');res.setHeader('Access-Control-Allow-Headers','Content-Type,Last-Event-ID');res.setHeader('Access-Control-Allow-Methods','GET,POST,OPTIONS');
    if(req.method==='OPTIONS'){res.writeHead(204);return res.end();}
    const path=new URL(req.url,'http://fixture').pathname;
    if(path.endsWith('/telemetry/stream')){if(streamHeld){res.writeHead(503);return res.end();}res.writeHead(200,{'Content-Type':'text/event-stream'});clients.add(res);push();req.on('close',()=>clients.delete(res));return;}
    const json=(value,status=200)=>{res.writeHead(status,{'Content-Type':'application/json'});res.end(JSON.stringify(value));};
    let body='';for await(const chunk of req)body+=chunk;
    if(req.method!=='GET')writes.push({path,body});
    if(path.endsWith('/telemetry/snapshot'))return json(snapshot());
    if(path.endsWith('/stations'))return json(stations);
    if(path.endsWith('/vehicles'))return json(vehicles);
    if(path.endsWith('/routes'))return json(trips.slice(0,3).map(trip=>({...detail(trip).route,stopCount:2})));
    if(path.endsWith('/trips'))return json(trips);
    if(path.endsWith('/eta')){const id=Number(path.split('/')[4]),run=runs.get(id),now=new Date().toISOString();return json({tripId:id,routeId:id,calculatedAt:now,source:'ROUTE_SNAPSHOT',status:'AVAILABLE',nextStopSequence:2,baselineRemainingSeconds:600,totalRemainingSeconds:600,
      trafficObservedAt:null,trafficFetchedAt:null,affectedSegments:[],warning:null,stops:[{sequenceNumber:2,stationName:'Trạm cuối · fixture',state:'NEXT',etaSeconds:600-(run?.elapsedSeconds??0),etaAt:null,source:'ROUTE_SNAPSHOT'}]});}
    if(path.includes('/traffic/'))return json({source:'HERE_LIVE',status:'AVAILABLE',results:[]});
    if(path.endsWith('/check-ins'))return json({tripId:Number(path.split('/')[4]),revision:0,visits:[]});
    const match=path.match(/\/trips\/(\d+)(?:\/simulation\/(\w+))?$/);
    if(match){const trip=trips.find(t=>t.id===Number(match[1]));if(!trip)return json({detail:'Fixture trip missing'},404);
      if(!match[2]){detailGets.push(trip.id);return trip.id===failedTrip?json({detail:'Fixture start station unavailable'},503):json(detail(trip));}
      const action=match[2];let run=runs.get(trip.id);
      if(action==='play'){
        if(trips.some(t=>t.vehicleId===trip.vehicleId&&t.id!==trip.id&&t.status==='IN_PROGRESS'))return json({detail:'Xe đang chạy chuyến khác.'},409);
        if(!run){run={id:trip.id,tripId:trip.id,status:'PAUSED',elapsedSeconds:0,durationSeconds:600,multiplier:1,errorMessage:null,replacementTripId:null};runs.set(trip.id,run);}
        run.status='RUNNING';trip.status='IN_PROGRESS';trip.startedAt??=new Date().toISOString();
      }else if(!run)return json({detail:'Chưa có phiên mô phỏng.'},409);
      else if(action==='pause')run.status='PAUSED';
      else if(action==='speed')run.multiplier=JSON.parse(body).multiplier;
      else if(action==='stop'){run.status='STOPPED';trip.status='CANCELLED';}
      update(run);push();return json(run);
    }
    return json([]);
  });
  await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
  return {api:`http://127.0.0.1:${server.address().port}/api/v1`,snapshot,writes,runs,detailGets,failTrip:id=>{failedTrip=id;},invalidRoute:id=>{invalidRoute=id;},
    completeTrip:id=>{trips.find(trip=>trip.id===id).status='COMPLETED';runs.get(id).status='COMPLETED';push();},
    disconnect:value=>{streamHeld=value;if(value){for(const client of clients)client.end();clients.clear();}},
    close:async()=>{clearInterval(timer);for(const client of clients)client.end();server.closeAllConnections();await new Promise(resolve=>server.close(resolve));}};
}
