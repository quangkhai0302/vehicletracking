<script setup lang="ts">
import { computed } from 'vue';
import { ArrowUpRight, CarFront, Edit3, SearchX, Trash2, UserRound } from '@lucide/vue';
import {
  TRIP_STATUS_LABELS,
  vehicleTypeLabel,
  type Driver,
  type FleetVehicle,
  type TripSummary,
} from '@/features/fleet/types/fleet';
import type { FleetTab } from '@/features/fleet/composables/useFleetWorkspace';
import {
  displayTripTime,
  tripDispatchLabel,
  tripReferenceTime,
} from '@/features/fleet/utils/tripTime';
const props = defineProps<{
  tab: FleetTab;
  vehicles: FleetVehicle[];
  allVehicles: FleetVehicle[];
  drivers: Driver[];
  trips: TripSummary[];
  onEditVehicle: (vehicle: FleetVehicle) => void;
  onDeactivateVehicle: (vehicle: FleetVehicle) => void;
  onVehicleTrips: (id: number) => void;
  onEditDriver: (driver: Driver) => void;
  onDeactivateDriver: (driver: Driver) => void;
  onTrip: (id: number) => void;
}>();
const count = computed(() =>
  props.tab === 'vehicles'
    ? props.vehicles.length
    : props.tab === 'drivers'
      ? props.drivers.length
      : props.trips.length,
);
const headers = computed(() =>
  props.tab === 'vehicles'
    ? ['Phương tiện', 'Loại xe', 'Tài xế phụ trách', 'Trạng thái', 'Thao tác']
    : props.tab === 'drivers'
      ? ['Tài xế', 'Giấy phép lái xe', 'Phương tiện', 'Trạng thái', 'Thao tác']
      : ['Chuyến đi / Tuyến', 'Phân công', 'Hình thức / thời gian', 'Trạng thái', 'Thao tác'],
);
const assignedVehicle = (id: number) =>
  props.allVehicles.find((vehicle) => vehicle.active && vehicle.driver?.id === id);
</script>
<template>
  <div
    v-if="!count"
    class="business-empty"
  >
    <SearchX :size="32" />
    <h3>Chưa có dữ liệu phù hợp</h3>
    <p>Thử thay đổi bộ lọc hoặc thêm bản ghi mới để bắt đầu.</p>
  </div>
  <div
    v-else
    class="management-table-wrap"
  >
    <table class="management-table">
      <caption class="business-sr-only">
        {{
          tab === 'vehicles'
            ? 'Danh sách phương tiện'
            : tab === 'drivers'
              ? 'Danh sách tài xế'
              : 'Danh sách chuyến đi'
        }}
      </caption>
      <thead>
        <tr>
          <th
            v-for="label in headers"
            :key="label"
            scope="col"
          >
            {{ label }}
          </th>
        </tr>
      </thead>
      <tbody>
        <template v-if="tab === 'vehicles'"
          ><tr
            v-for="vehicle in vehicles"
            :key="vehicle.id"
            :class="{ 'is-inactive': !vehicle.active }"
          >
            <td data-label="Phương tiện">
              <div class="management-identity">
                <span class="management-avatar"><CarFront :size="21" /></span>
                <div>
                  <strong>{{ vehicle.plateNumber }}</strong
                  ><small>{{ vehicle.name }}</small>
                </div>
              </div>
            </td>
            <td data-label="Loại xe">{{ vehicleTypeLabel(vehicle.vehicleType) }}</td>
            <td data-label="Tài xế phụ trách">
              <template v-if="vehicle.driver?.fullName != null">{{
                vehicle.driver.fullName
              }}</template
              ><span
                v-else
                class="business-unassigned"
                >Chưa phân công</span
              >
            </td>
            <td data-label="Trạng thái">
              <span :class="`business-status ${vehicle.active ? 'success' : 'neutral'}`"
                ><i aria-hidden="true" />{{
                  vehicle.active ? 'Đang hoạt động' : 'Đã ngừng hoạt động'
                }}</span
              >
            </td>
            <td data-label="Thao tác">
              <div class="management-row-actions">
                <button
                  title="Xem chuyến đi"
                  :aria-label="`Xem chuyến xe ${vehicle.plateNumber}`"
                  @click="onVehicleTrips(vehicle.id)"
                >
                  <ArrowUpRight :size="17" /></button
                ><template v-if="vehicle.active"
                  ><button
                    :aria-label="`Sửa xe ${vehicle.plateNumber}`"
                    @click="onEditVehicle(vehicle)"
                  >
                    <Edit3 :size="16" /></button
                  ><button
                    class="danger"
                    :aria-label="`Ngừng sử dụng xe ${vehicle.plateNumber}`"
                    @click="onDeactivateVehicle(vehicle)"
                  >
                    <Trash2 :size="16" /></button
                ></template>
              </div>
            </td></tr
        ></template>
        <template v-if="tab === 'drivers'"
          ><tr
            v-for="driver in drivers"
            :key="driver.id"
            :class="{ 'is-inactive': !driver.active }"
          >
            <td data-label="Tài xế">
              <div class="management-identity">
                <span class="management-avatar person"><UserRound :size="20" /></span>
                <div>
                  <strong>{{ driver.fullName }}</strong
                  ><small>{{ driver.phoneNumber }}</small>
                </div>
              </div>
            </td>
            <td data-label="Giấy phép lái xe">
              <span class="management-code">{{ driver.licenseNumber }}</span>
            </td>
            <td data-label="Phương tiện">
              <template v-if="assignedVehicle(driver.id)">{{
                assignedVehicle(driver.id)?.plateNumber
              }}</template
              ><span
                v-else
                class="business-unassigned"
                >Chưa phân công</span
              >
            </td>
            <td data-label="Trạng thái">
              <span :class="`business-status ${driver.active ? 'success' : 'neutral'}`"
                ><i aria-hidden="true" />{{
                  driver.active ? 'Đang hoạt động' : 'Đã ngừng hoạt động'
                }}</span
              >
            </td>
            <td data-label="Thao tác">
              <div class="management-row-actions">
                <template v-if="driver.active"
                  ><button
                    :aria-label="`Sửa tài xế ${driver.fullName}`"
                    @click="onEditDriver(driver)"
                  >
                    <Edit3 :size="16" /></button
                  ><button
                    class="danger"
                    :aria-label="`Ngừng tài xế ${driver.fullName}`"
                    @click="onDeactivateDriver(driver)"
                  >
                    <Trash2 :size="16" /></button></template
                ><span v-else>—</span>
              </div>
            </td>
          </tr></template
        >
        <template v-if="tab === 'trips'"
          ><tr
            v-for="trip in trips"
            :key="trip.id"
            :class="['trip-row', trip.status.toLowerCase()]"
          >
            <td data-label="Chuyến đi / Tuyến">
              <div class="management-trip">
                <span class="management-code">#{{ trip.id }}</span
                ><strong>{{ trip.routeName }}</strong>
              </div>
            </td>
            <td data-label="Phân công">
              <div class="management-stacked">
                <strong>{{ trip.vehiclePlateNumber }}</strong
                ><small>{{ trip.driver?.fullName ?? 'Chưa gán tài xế' }}</small>
              </div>
            </td>
            <td data-label="Hình thức / thời gian">
              <div class="management-stacked">
                <strong>{{ tripDispatchLabel(trip) }}</strong>
                <time :datetime="tripReferenceTime(trip)">{{
                  displayTripTime(tripReferenceTime(trip))
                }}</time>
              </div>
            </td>
            <td data-label="Trạng thái">
              <span :class="`business-status ${trip.status.toLowerCase()}`"
                ><i aria-hidden="true" />{{ TRIP_STATUS_LABELS[trip.status] }}</span
              >
            </td>
            <td data-label="Thao tác">
              <button
                class="management-detail-button"
                :aria-label="`Mở chi tiết chuyến ${trip.id}, ${trip.routeName}`"
                @click="onTrip(trip.id)"
              >
                Chi tiết <ArrowUpRight :size="15" />
              </button>
            </td></tr
        ></template>
      </tbody>
    </table>
    <div class="management-table-footer">
      Hiển thị {{ count }}
      {{ tab === 'vehicles' ? 'phương tiện' : tab === 'drivers' ? 'tài xế' : 'chuyến đi' }}
    </div>
  </div>
</template>
