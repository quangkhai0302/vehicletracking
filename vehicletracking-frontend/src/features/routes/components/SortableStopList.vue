<script setup lang="ts">
import { ref, shallowRef, useId, watch } from 'vue';
import { ArrowDown, ArrowUp, GripVertical, MapPin, Trash2 } from '@lucide/vue';
import type { RouteDraftStop } from '@/features/routes/types/route';
import type { Station } from '@/features/stations/types/station';
const props = defineProps<{
  stops: RouteDraftStop[];
  stations: Station[];
  disabled: boolean;
  onChange: (stops: RouteDraftStop[]) => void;
  selectedId: string | null;
  onFocusStop: (id: string) => void;
}>();
const instructionsId = useId(),
  list = shallowRef<HTMLOListElement | null>(null);
const grabbedId = ref<string | null>(null),
  dropTarget = ref<string | null>(null),
  announcement = ref('');
let original: RouteDraftStop[] = [],
  keyboard = false;
watch(
  () => props.selectedId,
  (id) => {
    if (id)
      list.value
        ?.querySelector<HTMLElement>(`[data-stop-id="${id}"]`)
        ?.scrollIntoView({ block: 'nearest' });
  },
  { immediate: true, flush: 'post' },
);
const role = (index: number) =>
  index === 0 ? 'start' : index === props.stops.length - 1 ? 'end' : 'stop';
const move = (from: number, to: number) => {
  if (props.disabled || from === to || to < 0 || to >= props.stops.length) return;
  const next = [...props.stops];
  next.splice(to, 0, next.splice(from, 1)[0]);
  props.onChange(next);
  announcement.value = `Đã chuyển điểm ${from + 1} đến vị trí ${to + 1} trên ${props.stops.length}.`;
};
const endDrag = () => {
  grabbedId.value = null;
  dropTarget.value = null;
  keyboard = false;
};
const dragOver = (event: DragEvent, id: string) => {
  if (!props.disabled && grabbedId.value) {
    event.preventDefault();
    if (event.dataTransfer) event.dataTransfer.dropEffect = 'move';
    dropTarget.value = id;
  }
};
const drop = (index: number) => {
  if (grabbedId.value)
    move(
      props.stops.findIndex((item) => item.id === grabbedId.value),
      index,
    );
  endDrag();
};
const dragStart = (event: DragEvent, id: string) => {
  original = props.stops;
  keyboard = false;
  grabbedId.value = id;
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move';
    event.dataTransfer.setData('text/plain', id);
  }
};
const keydown = (event: KeyboardEvent, id: string, index: number) => {
  if (event.key === ' ' || event.key === 'Enter') {
    event.preventDefault();
    if (grabbedId.value === id) {
      endDrag();
      announcement.value = `Đã thả điểm tại vị trí ${index + 1}.`;
    } else {
      original = props.stops;
      keyboard = true;
      grabbedId.value = id;
      announcement.value = `Đã nhấc điểm ${index + 1}. Dùng mũi tên để di chuyển.`;
    }
  } else if (
    keyboard &&
    grabbedId.value === id &&
    (event.key === 'ArrowUp' || event.key === 'ArrowDown')
  ) {
    event.preventDefault();
    move(index, index + (event.key === 'ArrowUp' ? -1 : 1));
  } else if (event.key === 'Escape' && grabbedId.value) {
    event.preventDefault();
    if (keyboard) props.onChange(original);
    endDrag();
    announcement.value = 'Đã hủy thay đổi thứ tự.';
  }
};
const change = (id: string, field: 'stationId' | 'dwellDurationSeconds', event: Event) => {
  props.onChange(
    props.stops.map((item) =>
      item.id === id
        ? { ...item, [field]: Number((event.target as HTMLInputElement).value) }
        : item,
    ),
  );
};
</script>
<template>
  <p
    :id="instructionsId"
    class="sort-instructions"
  >
    Kéo tay nắm để đổi thứ tự. Bàn phím: Space để nhấc, ↑ ↓ để di chuyển, Enter để thả, Esc để hủy.
  </p>
  <ol
    ref="list"
    class="sortable-stop-list"
  >
    <li
      v-for="(stop, index) in stops"
      :key="stop.id"
      :data-stop-id="stop.id"
      :data-station-id="stop.stationId"
      :class="`sortable-stop ${selectedId === stop.id ? 'selected' : ''} ${grabbedId === stop.id ? 'grabbed' : ''} ${dropTarget === stop.id ? 'drop-target' : ''}`"
      @dragover="dragOver($event, stop.id)"
      @drop.prevent="drop(index)"
    >
      <div class="sortable-stop-top">
        <button
          type="button"
          class="drag-handle"
          :draggable="!disabled"
          :disabled="disabled"
          :aria-label="`Sắp xếp điểm ${index + 1}`"
          :aria-describedby="instructionsId"
          :aria-pressed="grabbedId === stop.id"
          @dragstart="dragStart($event, stop.id)"
          @dragend="endDrag"
          @keydown="keydown($event, stop.id, index)"
        >
          <GripVertical :size="17" />
        </button>
        <span :class="`stop-order ${role(index)}`">{{ index + 1 }}</span
        ><span :class="`stop-badge ${role(index)}`">{{
          role(index) === 'start' ? 'Điểm đầu' : role(index) === 'end' ? 'Điểm cuối' : 'Trạm dừng'
        }}</span>
        <button
          type="button"
          class="stop-locate"
          :aria-label="`Xem điểm ${index + 1} trên bản đồ`"
          @click="onFocusStop(stop.id)"
        >
          <MapPin :size="14" />
        </button>
      </div>
      <select
        class="form-select"
        :value="stop.stationId"
        :disabled="disabled"
        :aria-label="`Chọn trạm cho điểm dừng ${index + 1}`"
        @change="change(stop.id, 'stationId', $event)"
      >
        <option
          v-if="!stations.some((station) => station.id === stop.stationId)"
          :value="stop.stationId"
        >
          Trạm không còn hoạt động
        </option>
        <option
          v-for="station in stations"
          :key="station.id"
          :value="station.id"
        >
          {{ station.name }}
        </option>
      </select>
      <div class="sortable-stop-bottom">
        <label
          >Dừng
          <template v-if="role(index) === 'stop'"
            ><input
              type="number"
              :min="0"
              :max="3600"
              :step="1"
              :value="stop.dwellDurationSeconds"
              :disabled="disabled"
              :aria-label="`Thời gian dừng cho điểm ${index + 1} (giây)`"
              @input="change(stop.id, 'dwellDurationSeconds', $event)"
            />giây</template
          ><span v-else>0 giây</span></label
        >
        <div class="stop-actions">
          <button
            type="button"
            class="btn-icon-small"
            :disabled="disabled || index === 0"
            :aria-label="`Di chuyển điểm ${index + 1} lên`"
            @click="move(index, index - 1)"
          >
            <ArrowUp :size="14" /></button
          ><button
            type="button"
            class="btn-icon-small"
            :disabled="disabled || index === stops.length - 1"
            :aria-label="`Di chuyển điểm ${index + 1} xuống`"
            @click="move(index, index + 1)"
          >
            <ArrowDown :size="14" /></button
          ><button
            type="button"
            class="btn-icon-small danger"
            :disabled="disabled || stops.length <= 2"
            :aria-label="`Xóa điểm dừng ${index + 1}`"
            @click="onChange(stops.filter((item) => item.id !== stop.id))"
          >
            <Trash2 :size="14" />
          </button>
        </div>
      </div>
    </li>
  </ol>
  <div
    class="sr-only"
    role="status"
  >
    {{ announcement }}
  </div>
</template>
