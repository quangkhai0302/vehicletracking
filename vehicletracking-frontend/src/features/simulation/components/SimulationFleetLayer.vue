<script setup lang="ts">
import { computed, watch } from 'vue';
import L from 'leaflet';
import type { WaitingSimulationVehicle } from '@/features/simulation/composables/useSimulationFleet';
import type { VehicleType } from '@/features/fleet/types/fleet';
import { vehicleMarkerGlyph } from '@/features/fleet/utils/vehiclePresentation';

const props = defineProps<{
  map: L.Map | null;
  mapReady: boolean;
  visible: boolean;
  vehicles: WaitingSimulationVehicle[];
  onSelect: (tripId: number) => void;
}>();
// Snapshot SSE arrives every second. Only rebuild static waiting markers when
// their actual assignments/coordinates change, preserving an open station popup.
const signature = computed(() =>
  JSON.stringify(
    props.vehicles.map(({ trip, start }) => ({
      tripId: trip.id,
      plate: trip.vehiclePlateNumber,
      vehicleType: trip.vehicleType ?? 'CAR',
      routeName: trip.routeName,
      station: start.stationName,
      latitude: start.latitude,
      longitude: start.longitude,
    })),
  ),
);
watch(
  [() => props.map, () => props.mapReady, () => props.visible, signature],
  ([map, mapReady, visible, key], _old, cleanup) => {
    if (!visible || !mapReady || !map) return;
    const rows = JSON.parse(key) as {
      tripId: number;
      plate: string;
      vehicleType: VehicleType;
      routeName: string;
      station: string;
      latitude: number;
      longitude: number;
    }[];
    const groups = new Map<string, typeof rows>();
    for (const row of rows) {
      const key = `${row.latitude},${row.longitude}`;
      groups.set(key, [...(groups.get(key) ?? []), row]);
    }
    const layer = L.layerGroup().addTo(map);
    const cleanups: (() => void)[] = [];
    for (const group of groups.values()) {
      const first = group[0];
      const groupTypes = [...new Set(group.map((row) => row.vehicleType))];
      const markerGlyphs = groupTypes.map(vehicleMarkerGlyph).join('');
      const marker = L.marker([first.latitude, first.longitude], {
        keyboard: true,
        zIndexOffset: 900,
        title:
          group.length > 1
            ? `${group.length} xe chờ tại ${first.station}`
            : `Xe ${first.plate} chờ tại ${first.station}`,
        icon: L.divIcon({
          className: 'simulation-waiting-icon',
          iconSize: [44, 44],
          iconAnchor: [22, 22],
          html: `<div class="simulation-waiting-marker" data-waiting-count="${group.length}" data-vehicle-types="${groupTypes.join(' ')}">${markerGlyphs}${group.length > 1 ? `<b>${group.length}</b>` : ''}</div>`,
        }),
      }).addTo(layer);
      const label = document.createElement('span');
      label.textContent = `${group.length > 1 ? `${group.length} xe` : first.plate} · Chờ xuất phát`;
      marker.bindTooltip(label, { direction: 'top', offset: [0, -22] });
      if (group.length === 1) marker.on('click', () => props.onSelect(first.tripId));
      else {
        const popup = document.createElement('div');
        popup.className = 'simulation-station-picker';
        const heading = document.createElement('strong');
        heading.textContent = `${first.station} · ${group.length} xe chờ`;
        const help = document.createElement('p');
        help.textContent = 'Chọn xe để mở điều khiển mô phỏng.';
        popup.append(heading, help);
        for (const row of group) {
          const button = document.createElement('button');
          button.type = 'button';
          button.dataset.waitingTrip = String(row.tripId);
          button.textContent = `${row.plate} · ${row.routeName}`;
          button.onclick = () => {
            map.closePopup();
            props.onSelect(row.tripId);
          };
          cleanups.push(() => {
            button.onclick = null;
          });
          popup.append(button);
        }
        marker.bindPopup(popup, { className: 'simulation-station-popup', maxWidth: 260 });
      }
      cleanups.push(() => marker.off());
    }
    cleanup(() => {
      cleanups.forEach((dispose) => dispose());
      layer.clearLayers();
      layer.remove();
    });
  },
  { immediate: true },
);
</script>
<script lang="ts">
export default { render: () => null };
</script>
