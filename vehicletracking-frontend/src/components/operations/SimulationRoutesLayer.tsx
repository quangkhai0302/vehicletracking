import { useEffect, useRef, type RefObject } from 'react';
import L from 'leaflet';
import type { SimulationFleetRoute } from '../../hooks/useSimulationFleet';
import { simulationRouteColor } from '../../utils/simulationFleet';

interface RouteRow {
  tripId: number; vehicleId: number; plate: string; name: string; segments: [number,number][][];
}

export function SimulationRoutesLayer({ mapRef, mapReady, visible, routes, selectedTripId, onSelect }: {
  mapRef: RefObject<L.Map | null>; mapReady: boolean; visible: boolean;
  routes: SimulationFleetRoute[]; selectedTripId: number | null; onSelect: (tripId: number) => void;
}) {
  const selectRef=useRef(onSelect);
  useEffect(()=>{selectRef.current=onSelect;},[onSelect]);
  // Telemetry updates must not rebuild static geometry or close an open route picker.
  const signature=JSON.stringify(routes.map(({trip,segments})=>({tripId:trip.id,vehicleId:trip.vehicleId,
    plate:trip.vehiclePlateNumber,name:trip.routeName,segments})));
  useEffect(()=>{
    const map=mapRef.current;
    if (!map || !mapReady || !visible || selectedTripId === null) return;
    const rows=(JSON.parse(signature) as RouteRow[]).filter(row => row.tripId === selectedTripId);
    const pane=map.getPane('simulationRoutesPane') ?? map.createPane('simulationRoutesPane');
    pane.style.zIndex='455'; pane.style.pointerEvents='none';
    const renderer=L.svg({pane:'simulationRoutesPane'});
    const layer=L.layerGroup().addTo(map);
    const cleanups: (()=>void)[]=[];
    let picker: L.Popup | null=null;
    let clearButtons=()=>{};
    const closePicker=()=>{picker?.remove();picker=null;clearButtons();clearButtons=()=>{};};
    const choose=(row: RouteRow,point: L.LatLng)=>{
      closePicker();
      const pixel=map.latLngToLayerPoint(point);
      const nearby=rows.filter(candidate=>candidate.tripId===row.tripId || candidate.segments.some(segment=>
        segment.slice(1).some((end,i)=>L.LineUtil.pointToSegmentDistance(pixel,
          map.latLngToLayerPoint(segment[i]),map.latLngToLayerPoint(end))<=10)));
      if (nearby.length===1) { selectRef.current(row.tripId); return; }
      const content=document.createElement('div'); content.className='simulation-station-picker';
      const title=document.createElement('strong'); title.textContent=`${nearby.length} xe có tuyến qua đây`;
      content.append(title);
      const buttons=nearby.map(candidate=>{
        const button=document.createElement('button'); button.type='button';
        button.dataset.simulationRouteChoice=String(candidate.tripId);
        button.textContent=`${candidate.plate} · #${candidate.tripId} · ${candidate.name}`;
        button.onclick=()=>{closePicker();selectRef.current(candidate.tripId);};
        content.append(button);return button;
      });
      clearButtons=()=>buttons.forEach(button=>{button.onclick=null;});
      picker=L.popup({className:'simulation-station-popup',maxWidth:260}).setLatLng(point).setContent(content).openOn(map);
      picker.on('remove',clearButtons);
    };
    // Only the selected vehicle's trip is allowed to contribute route geometry.
    rows.sort((a,b)=>Number(a.tripId===selectedTripId)-Number(b.tripId===selectedTripId));
    for (const row of rows) {
      const selected=row.tripId===selectedTripId;
      const options={pane:'simulationRoutesPane',renderer,bubblingMouseEvents:false};
      L.polyline(row.segments,{...options,color:selected?'#1967d2':'#3c4043',weight:selected?8:5.5,opacity:selected?0.96:0.8,interactive:false}).addTo(layer);
      const path=L.polyline(row.segments,{...options,color:simulationRouteColor(row.vehicleId,selected),
        weight:selected?5:3.5,opacity:1,className:'simulation-route-path',interactive:true}).addTo(layer);
      const label=document.createElement('span');
      label.textContent=`${row.plate} · Chuyến #${row.tripId} · ${row.name}${selected?' · Đang chọn':''}`;
      path.bindTooltip(label,{sticky:true});
      path.on('click',(event: L.LeafletMouseEvent)=>choose(row,event.latlng));
      const element=path.getElement();
      const keydown=(event: Event)=>{
        const key=event as KeyboardEvent;
        if (key.key!=='Enter' && key.key!==' ') return;
        key.preventDefault();key.stopPropagation();
        selectRef.current(row.tripId);
      };
      if (element) {
        element.setAttribute('data-simulation-route-trip',String(row.tripId));
        element.setAttribute('tabindex','0');element.setAttribute('role','button');
        element.setAttribute('aria-label',`Chọn xe ${row.plate}, chuyến #${row.tripId}, tuyến ${row.name}`);
        element.setAttribute('aria-pressed',String(selected));
        element.addEventListener('keydown',keydown);
      }
      cleanups.push(()=>{element?.removeEventListener('keydown',keydown);path.off();path.unbindTooltip();});
    }
    return ()=>{closePicker();cleanups.forEach(cleanup=>cleanup());layer.clearLayers();layer.remove();renderer.remove();};
  },[mapRef,mapReady,visible,signature,selectedTripId]);
  return null;
}
