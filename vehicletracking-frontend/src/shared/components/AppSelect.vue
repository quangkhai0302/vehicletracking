<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { Check, ChevronDown } from '@lucide/vue';

export interface SelectOption {
  value: string | number;
  label: string;
  disabled?: boolean;
}

const props = withDefaults(
  defineProps<{
    modelValue?: string | number | null;
    options: (SelectOption | string | number)[];
    placeholder?: string;
    disabled?: boolean;
    required?: boolean;
    id?: string;
    ariaLabel?: string;
  }>(),
  {
    modelValue: '',
    placeholder: 'Chọn...',
    disabled: false,
    required: false,
    id: undefined,
    ariaLabel: undefined,
  },
);

const emit = defineEmits<{
  (e: 'update:modelValue', value: string | number): void;
  (e: 'change', value: string | number): void;
}>();

const isOpen = ref(false);
const triggerRef = ref<HTMLButtonElement | null>(null);
const listboxRef = ref<HTMLUListElement | null>(null);
const activeIndex = ref(-1);

const normalizedOptions = computed<SelectOption[]>(() =>
  props.options.map((opt) => {
    if (typeof opt === 'object' && opt !== null && 'value' in opt) {
      return opt as SelectOption;
    }
    return { value: opt, label: String(opt) };
  }),
);

const selectedOption = computed(() =>
  normalizedOptions.value.find((opt) => String(opt.value) === String(props.modelValue)),
);

const displayText = computed(() => {
  if (selectedOption.value) return selectedOption.value.label;
  return props.placeholder;
});

function toggleDropdown() {
  if (props.disabled) return;
  if (isOpen.value) {
    closeDropdown();
  } else {
    openDropdown();
  }
}

function openDropdown() {
  isOpen.value = true;
  const currentIdx = normalizedOptions.value.findIndex(
    (opt) => String(opt.value) === String(props.modelValue),
  );
  activeIndex.value = currentIdx >= 0 ? currentIdx : 0;
  nextTick(() => {
    scrollActiveIntoView();
  });
}

function closeDropdown() {
  isOpen.value = false;
  activeIndex.value = -1;
}

function selectOption(option: SelectOption) {
  if (option.disabled || props.disabled) return;
  emit('update:modelValue', option.value);
  emit('change', option.value);
  closeDropdown();
  triggerRef.value?.focus();
}

function onNativeChange(e: Event) {
  const val = (e.target as HTMLSelectElement).value;
  const matched = normalizedOptions.value.find((opt) => String(opt.value) === val);
  const finalVal = matched ? matched.value : val;
  emit('update:modelValue', finalVal);
  emit('change', finalVal);
}

function scrollActiveIntoView() {
  if (!listboxRef.value || activeIndex.value < 0) return;
  const activeEl = listboxRef.value.children[activeIndex.value] as HTMLElement | undefined;
  activeEl?.scrollIntoView({ block: 'nearest' });
}

function onKeyDown(e: KeyboardEvent) {
  if (props.disabled) return;

  if (!isOpen.value) {
    if (e.key === 'ArrowDown' || e.key === 'ArrowUp' || e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      openDropdown();
    }
    return;
  }

  const count = normalizedOptions.value.length;
  if (count === 0) return;

  if (e.key === 'Escape') {
    e.preventDefault();
    closeDropdown();
    triggerRef.value?.focus();
  } else if (e.key === 'Tab') {
    closeDropdown();
  } else if (e.key === 'ArrowDown') {
    e.preventDefault();
    activeIndex.value = (activeIndex.value + 1) % count;
    scrollActiveIntoView();
  } else if (e.key === 'ArrowUp') {
    e.preventDefault();
    activeIndex.value = (activeIndex.value - 1 + count) % count;
    scrollActiveIntoView();
  } else if (e.key === 'Home') {
    e.preventDefault();
    activeIndex.value = 0;
    scrollActiveIntoView();
  } else if (e.key === 'End') {
    e.preventDefault();
    activeIndex.value = count - 1;
    scrollActiveIntoView();
  } else if (e.key === 'Enter' || e.key === ' ') {
    e.preventDefault();
    if (activeIndex.value >= 0 && activeIndex.value < count) {
      selectOption(normalizedOptions.value[activeIndex.value]);
    }
  }
}

function handleClickOutside(event: MouseEvent) {
  const target = event.target as Node | null;
  if (
    isOpen.value &&
    triggerRef.value &&
    !triggerRef.value.contains(target) &&
    listboxRef.value &&
    !listboxRef.value.contains(target)
  ) {
    closeDropdown();
  }
}

onMounted(() => {
  window.addEventListener('mousedown', handleClickOutside);
});

onBeforeUnmount(() => {
  window.removeEventListener('mousedown', handleClickOutside);
});

watch(
  () => props.disabled,
  (disabled) => {
    if (disabled && isOpen.value) closeDropdown();
  },
);
</script>

<template>
  <div
    class="app-select"
    :class="{ 'is-open': isOpen, 'is-disabled': disabled }"
    @keydown="onKeyDown"
  >
    <button
      :id="id"
      ref="triggerRef"
      type="button"
      class="app-select-trigger"
      :class="{ 'has-value': !!selectedOption }"
      :aria-expanded="isOpen"
      :aria-haspopup="'listbox'"
      :aria-label="ariaLabel"
      :disabled="disabled"
      @click="toggleDropdown"
    >
      <span class="app-select-label">{{ displayText }}</span>
      <ChevronDown
        :size="16"
        class="app-select-icon"
      />
    </button>

    <select
      tabindex="-1"
      aria-hidden="true"
      class="app-select-native"
      :value="modelValue ?? ''"
      :disabled="disabled"
      :required="required"
      @change="onNativeChange"
    >
      <option
        v-for="opt in normalizedOptions"
        :key="String(opt.value)"
        :value="opt.value"
        :disabled="opt.disabled"
      >
        {{ opt.label }}
      </option>
    </select>

    <transition name="app-select-fade">
      <ul
        v-if="isOpen"
        ref="listboxRef"
        role="listbox"
        class="app-select-dropdown"
        :aria-activedescendant="activeIndex >= 0 ? `option-${activeIndex}` : undefined"
      >
        <li
          v-for="(option, index) in normalizedOptions"
          :id="`option-${index}`"
          :key="String(option.value)"
          role="option"
          class="app-select-option"
          :class="{
            'is-selected': String(option.value) === String(modelValue),
            'is-active': index === activeIndex,
            'is-disabled': option.disabled,
          }"
          :aria-selected="String(option.value) === String(modelValue)"
          @click="selectOption(option)"
          @mouseenter="activeIndex = index"
        >
          <span class="option-label">{{ option.label }}</span>
          <Check
            v-if="String(option.value) === String(modelValue)"
            :size="15"
            class="option-check"
          />
        </li>
      </ul>
    </transition>
  </div>
</template>

<style scoped>
.app-select {
  position: relative;
  width: 100%;
  min-width: 0;
  display: inline-block;
  font-family: inherit;
}

.app-select-trigger {
  width: 100%;
  min-height: 40px;
  padding: 8px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  background: #ffffff;
  color: var(--text-primary, #0f172a);
  border: 1px solid var(--border-default, #cbd5e1);
  border-radius: 8px;
  font-size: 13.5px;
  font-weight: 500;
  line-height: 1.4;
  text-align: left;
  cursor: pointer;
  box-shadow: 0 1px 2px 0 rgb(0 0 0 / 0.02);
  transition:
    border-color 0.15s ease,
    box-shadow 0.15s ease;
  user-select: none;
}

.app-select-trigger:not(.has-value) .app-select-label {
  color: #94a3b8;
}

.app-select-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-select-icon {
  flex-shrink: 0;
  color: #64748b;
  transition: transform 0.18s ease;
}

.app-select.is-open .app-select-icon {
  transform: rotate(180deg);
}

.app-select-trigger:focus-visible,
.app-select.is-open .app-select-trigger {
  outline: none;
  border-color: #0284c7;
  box-shadow: 0 0 0 2px rgba(2, 132, 199, 0.16);
}

.app-select.is-disabled .app-select-trigger {
  opacity: 0.6;
  cursor: not-allowed;
  background: #f8fafc;
}

.app-select-dropdown {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  z-index: 1000;
  max-height: 240px;
  overflow-y: auto;
  margin: 0;
  padding: 4px;
  list-style: none;
  background: #ffffff;
  border: 1px solid var(--border-default, #cbd5e1);
  border-radius: 8px;
  box-shadow:
    0 10px 25px -5px rgba(15, 23, 42, 0.12),
    0 8px 10px -6px rgba(15, 23, 42, 0.06);
}

.app-select-option {
  min-height: 36px;
  padding: 8px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  border-radius: 6px;
  color: var(--text-primary, #0f172a);
  font-size: 13.5px;
  font-weight: 500;
  cursor: pointer;
  transition:
    background 0.12s ease,
    color 0.12s ease;
  user-select: none;
}

.app-select-option.is-active {
  background: #f1f5f9;
}

.app-select-option.is-selected {
  background: #f0f9ff;
  color: #0284c7;
  font-weight: 600;
}

.app-select-option.is-disabled {
  opacity: 0.5;
  cursor: not-allowed;
  background: transparent;
}

.option-check {
  flex-shrink: 0;
  color: #0284c7;
}

.app-select-fade-enter-active,
.app-select-fade-leave-active {
  transition:
    opacity 0.12s ease,
    transform 0.12s ease;
}

.app-select-fade-enter-from,
.app-select-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.app-select-native {
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
</style>
