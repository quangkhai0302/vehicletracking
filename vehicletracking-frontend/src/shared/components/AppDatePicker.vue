<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { Calendar as CalendarIcon, ChevronLeft, ChevronRight, X } from '@lucide/vue';

const props = withDefaults(
  defineProps<{
    modelValue?: string | null;
    placeholder?: string;
    min?: string;
    max?: string;
    disabled?: boolean;
    required?: boolean;
    id?: string;
    ariaLabel?: string;
  }>(),
  {
    modelValue: '',
    placeholder: 'dd/mm/yyyy',
    min: undefined,
    max: undefined,
    disabled: false,
    required: false,
    id: undefined,
    ariaLabel: undefined,
  },
);

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void;
  (e: 'change', value: string): void;
}>();

const isOpen = ref(false);
const containerRef = ref<HTMLDivElement | null>(null);

// Current view month & year in calendar popup
const viewDate = ref(new Date());

const weekdays = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];

function parseYmd(val?: string | null): Date | null {
  if (!val || typeof val !== 'string') return null;
  const parts = val.split('-');
  if (parts.length !== 3) return null;
  const y = Number(parts[0]);
  const m = Number(parts[1]) - 1;
  const d = Number(parts[2]);
  if (isNaN(y) || isNaN(m) || isNaN(d)) return null;
  return new Date(y, m, d);
}

function formatYmd(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

const selectedDate = computed(() => parseYmd(props.modelValue));

const displayValue = computed(() => {
  if (!selectedDate.value) return '';
  const d = selectedDate.value;
  const day = String(d.getDate()).padStart(2, '0');
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const year = d.getFullYear();
  return `${day}/${month}/${year}`;
});

const monthTitle = computed(() => {
  const month = viewDate.value.getMonth() + 1;
  const year = viewDate.value.getFullYear();
  return `Tháng ${month}, ${year}`;
});

interface DayCell {
  date: Date;
  ymd: string;
  dayNum: number;
  isCurrentMonth: boolean;
  isToday: boolean;
  isSelected: boolean;
  isDisabled: boolean;
}

const calendarDays = computed<DayCell[]>(() => {
  const year = viewDate.value.getFullYear();
  const month = viewDate.value.getMonth();

  // First day of current month
  const firstDay = new Date(year, month, 1);
  // Last day of current month
  const lastDay = new Date(year, month + 1, 0);

  // Day of week: 0 = Sun, 1 = Mon ... convert to Mon = 0, Sun = 6
  let firstDayOfWeek = firstDay.getDay() - 1;
  if (firstDayOfWeek < 0) firstDayOfWeek = 6;

  const todayYmd = formatYmd(new Date());
  const selectedYmd = props.modelValue || '';

  const cells: DayCell[] = [];

  // Previous month trailing days
  for (let i = firstDayOfWeek - 1; i >= 0; i--) {
    const d = new Date(year, month, -i);
    const ymd = formatYmd(d);
    cells.push({
      date: d,
      ymd,
      dayNum: d.getDate(),
      isCurrentMonth: false,
      isToday: ymd === todayYmd,
      isSelected: ymd === selectedYmd,
      isDisabled: isDateDisabled(ymd),
    });
  }

  // Current month days
  for (let d = 1; d <= lastDay.getDate(); d++) {
    const curDate = new Date(year, month, d);
    const ymd = formatYmd(curDate);
    cells.push({
      date: curDate,
      ymd,
      dayNum: d,
      isCurrentMonth: true,
      isToday: ymd === todayYmd,
      isSelected: ymd === selectedYmd,
      isDisabled: isDateDisabled(ymd),
    });
  }

  // Next month leading days to complete 42 cells (6 rows)
  const remaining = 42 - cells.length;
  for (let i = 1; i <= remaining; i++) {
    const d = new Date(year, month + 1, i);
    const ymd = formatYmd(d);
    cells.push({
      date: d,
      ymd,
      dayNum: d.getDate(),
      isCurrentMonth: false,
      isToday: ymd === todayYmd,
      isSelected: ymd === selectedYmd,
      isDisabled: isDateDisabled(ymd),
    });
  }

  return cells;
});

function isDateDisabled(ymd: string): boolean {
  if (props.min && ymd < props.min) return true;
  if (props.max && ymd > props.max) return true;
  return false;
}

function prevMonth() {
  viewDate.value = new Date(viewDate.value.getFullYear(), viewDate.value.getMonth() - 1, 1);
}

function nextMonth() {
  viewDate.value = new Date(viewDate.value.getFullYear(), viewDate.value.getMonth() + 1, 1);
}

function selectDay(cell: DayCell) {
  if (cell.isDisabled || props.disabled) return;
  emit('update:modelValue', cell.ymd);
  emit('change', cell.ymd);
  isOpen.value = false;
}

function selectToday() {
  const todayYmd = formatYmd(new Date());
  if (isDateDisabled(todayYmd)) return;
  viewDate.value = new Date();
  emit('update:modelValue', todayYmd);
  emit('change', todayYmd);
  isOpen.value = false;
}

function clearDate() {
  if (props.disabled) return;
  emit('update:modelValue', '');
  emit('change', '');
  isOpen.value = false;
}

function togglePopup() {
  if (props.disabled) return;
  if (isOpen.value) {
    isOpen.value = false;
  } else {
    if (selectedDate.value) {
      viewDate.value = new Date(selectedDate.value);
    } else {
      viewDate.value = new Date();
    }
    isOpen.value = true;
  }
}

function onNativeInput(e: Event) {
  const val = (e.target as HTMLInputElement).value;
  emit('update:modelValue', val);
  emit('change', val);
}

function onNativeChange(e: Event) {
  const val = (e.target as HTMLInputElement).value;
  emit('update:modelValue', val);
  emit('change', val);
}

function handleClickOutside(event: MouseEvent) {
  const target = event.target as Node | null;
  if (isOpen.value && containerRef.value && !containerRef.value.contains(target)) {
    isOpen.value = false;
  }
}

function onKeyDown(e: KeyboardEvent) {
  if (e.key === 'Escape' && isOpen.value) {
    isOpen.value = false;
  }
}

onMounted(() => {
  window.addEventListener('mousedown', handleClickOutside);
  window.addEventListener('keydown', onKeyDown);
});

onBeforeUnmount(() => {
  window.removeEventListener('mousedown', handleClickOutside);
  window.removeEventListener('keydown', onKeyDown);
});

watch(
  () => props.modelValue,
  (val) => {
    const parsed = parseYmd(val);
    if (parsed) {
      viewDate.value = new Date(parsed);
    }
  },
  { immediate: true },
);
</script>

<template>
  <div
    ref="containerRef"
    class="app-datepicker"
    :class="{ 'is-open': isOpen, 'is-disabled': disabled }"
  >
    <div
      class="app-datepicker-input-wrapper"
      @click="togglePopup"
    >
      <input
        :id="id"
        type="text"
        readonly
        class="app-datepicker-display"
        :value="displayValue"
        :placeholder="placeholder"
        :disabled="disabled"
        :aria-label="ariaLabel"
        :aria-expanded="isOpen"
        tabindex="0"
        @keydown.enter.prevent="togglePopup"
        @keydown.space.prevent="togglePopup"
      />
      <CalendarIcon
        :size="16"
        class="app-datepicker-icon"
      />
    </div>

    <!-- Hidden native date input to support form validation and automated tests -->
    <input
      tabindex="-1"
      type="date"
      class="app-datepicker-native"
      :value="modelValue ?? ''"
      :min="min"
      :max="max"
      :disabled="disabled"
      :required="required"
      @input="onNativeInput"
      @change="onNativeChange"
    />

    <transition name="app-datepicker-fade">
      <div
        v-if="isOpen"
        class="app-datepicker-popup"
        role="dialog"
        aria-modal="true"
        aria-label="Chọn ngày"
      >
        <!-- Header: Month title & Nav buttons -->
        <div class="calendar-header">
          <button
            type="button"
            class="nav-btn"
            aria-label="Tháng trước"
            @click.stop="prevMonth"
          >
            <ChevronLeft :size="16" />
          </button>
          <span class="month-title">{{ monthTitle }}</span>
          <button
            type="button"
            class="nav-btn"
            aria-label="Tháng sau"
            @click.stop="nextMonth"
          >
            <ChevronRight :size="16" />
          </button>
        </div>

        <!-- Weekdays row -->
        <div class="calendar-weekdays">
          <span
            v-for="day in weekdays"
            :key="day"
            class="weekday-label"
          >
            {{ day }}
          </span>
        </div>

        <!-- Days grid -->
        <div class="calendar-grid">
          <button
            v-for="cell in calendarDays"
            :key="cell.ymd"
            type="button"
            class="day-cell"
            :class="{
              'is-current-month': cell.isCurrentMonth,
              'is-other-month': !cell.isCurrentMonth,
              'is-today': cell.isToday,
              'is-selected': cell.isSelected,
              'is-disabled': cell.isDisabled,
            }"
            :disabled="cell.isDisabled"
            @click.stop="selectDay(cell)"
          >
            <span class="day-number">{{ cell.dayNum }}</span>
          </button>
        </div>

        <!-- Footer quick actions -->
        <div class="calendar-footer">
          <button
            type="button"
            class="action-btn text-btn"
            @click.stop="selectToday"
          >
            Hôm nay
          </button>
          <button
            v-if="modelValue"
            type="button"
            class="action-btn clear-btn"
            @click.stop="clearDate"
          >
            <X :size="13" /> Xóa
          </button>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.app-datepicker {
  position: relative;
  width: 100%;
  min-width: 0;
  display: inline-block;
  font-family: inherit;
}

.app-datepicker-input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  width: 100%;
  cursor: pointer;
}

.app-datepicker-display {
  width: 100%;
  min-height: 40px;
  padding: 8px 36px 8px 12px;
  background: #ffffff;
  color: var(--text-primary, #0f172a);
  border: 1px solid var(--border-default, #cbd5e1);
  border-radius: 8px;
  font-size: 13.5px;
  font-weight: 500;
  cursor: pointer;
  box-shadow: 0 1px 2px 0 rgb(0 0 0 / 0.02);
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
  user-select: none;
}

.app-datepicker-display::placeholder {
  color: #94a3b8;
}

.app-datepicker-icon {
  position: absolute;
  right: 12px;
  color: #64748b;
  pointer-events: none;
}

.app-datepicker-display:focus-visible,
.app-datepicker.is-open .app-datepicker-display {
  outline: none;
  border-color: #0284c7;
  box-shadow: 0 0 0 2px rgba(2, 132, 199, 0.16);
}

.app-datepicker.is-disabled .app-datepicker-display {
  opacity: 0.6;
  cursor: not-allowed;
  background: #f8fafc;
}

.app-datepicker-native {
  position: absolute !important;
  width: 1px !important;
  height: 1px !important;
  padding: 0 !important;
  margin: -1px !important;
  overflow: hidden !important;
  clip: rect(0, 0, 0, 0) !important;
  white-space: nowrap !important;
  border: 0 !important;
  pointer-events: none !important;
  opacity: 0 !important;
}

/* Calendar Popup */
.app-datepicker-popup {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  z-index: 1050;
  width: 280px;
  padding: 14px;
  background: #ffffff;
  border: 1px solid var(--border-default, #cbd5e1);
  border-radius: 12px;
  box-shadow:
    0 10px 25px -5px rgba(15, 23, 42, 0.15),
    0 8px 10px -6px rgba(15, 23, 42, 0.08);
  user-select: none;
}

.calendar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.month-title {
  font-size: 13.5px;
  font-weight: 700;
  color: var(--text-primary, #0f172a);
}

.nav-btn {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  background: transparent;
  border: 1px solid var(--border-default, #e2e8f0);
  border-radius: 6px;
  color: #64748b;
  cursor: pointer;
  transition: all 0.15s ease;
}

.nav-btn:hover {
  background: #f1f5f9;
  color: #0f172a;
}

.calendar-weekdays {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 2px;
  margin-bottom: 6px;
  text-align: center;
}

.weekday-label {
  font-size: 11px;
  font-weight: 600;
  color: #94a3b8;
  padding: 4px 0;
}

.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 2px;
}

.day-cell {
  height: 34px;
  display: grid;
  place-items: center;
  background: transparent;
  border: 0;
  border-radius: 6px;
  font-size: 12.5px;
  font-weight: 500;
  color: var(--text-primary, #0f172a);
  cursor: pointer;
  transition: all 0.12s ease;
  padding: 0;
}

.day-cell.is-other-month {
  color: #cbd5e1;
}

.day-cell:hover:not(.is-disabled):not(.is-selected) {
  background: #f1f5f9;
  color: #0284c7;
}

.day-cell.is-today:not(.is-selected) {
  font-weight: 700;
  color: #0284c7;
  background: #f0f9ff;
  border: 1px solid #bae6fd;
}

.day-cell.is-selected {
  background: #0284c7;
  color: #ffffff;
  font-weight: 700;
}

.day-cell.is-disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.calendar-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid #f1f5f9;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  background: transparent;
  border: 0;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  color: #0284c7;
  cursor: pointer;
  transition: background 0.12s ease;
}

.action-btn:hover {
  background: #f0f9ff;
}

.action-btn.clear-btn {
  color: #64748b;
}

.action-btn.clear-btn:hover {
  color: #ef4444;
  background: #fef2f2;
}

.app-datepicker-fade-enter-active,
.app-datepicker-fade-leave-active {
  transition:
    opacity 0.12s ease,
    transform 0.12s ease;
}

.app-datepicker-fade-enter-from,
.app-datepicker-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>
