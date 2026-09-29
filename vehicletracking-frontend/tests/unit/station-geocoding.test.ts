import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { effectScope, nextTick, ref } from 'vue';
import { mount } from '@vue/test-utils';
import * as api from '@/features/stations/api/stations';
import { useStationGeocoding } from '@/features/stations/composables/useStationGeocoding';
import { useStationWorkspace } from '@/features/stations/composables/useStationWorkspace';
import StationDrawer from '@/features/stations/components/StationDrawer.vue';
import {
  EMPTY_STATION_FORM,
  type Station,
  type StationAddressResult,
  type StationFormMode,
  type StationInput,
} from '@/features/stations/types/station';

vi.mock('@/features/stations/api/stations', () => ({
  reverseGeocodeStation: vi.fn(),
  fetchStations: vi.fn(),
  createStation: vi.fn(),
  updateStation: vi.fn(),
  deleteStation: vi.fn(),
}));

const scopes: ReturnType<typeof effectScope>[] = [];
const unmounts: (() => void)[] = [];
const result = { address: 'Địa chỉ tại điểm đã chọn', distanceMeters: 12.5 };
const station: Station = {
  id: 1,
  name: 'Trạm mẫu',
  address: 'Địa chỉ đã lưu',
  latitude: 10.8,
  longitude: 106.7,
  checkinRadiusMeters: 50,
  active: true,
  createdAt: '2026-09-29T01:00:00Z',
  updatedAt: '2026-09-29T01:00:00Z',
};
function scoped<T>(factory: () => T) {
  const scope = effectScope();
  scopes.push(scope);
  return scope.run(factory)!;
}
function setup() {
  const form = ref({ ...EMPTY_STATION_FORM }),
    mode = ref<StationFormMode>('closed');
  const state = scoped(() => useStationGeocoding(form, mode));
  const point = async (latitude = '10.8', longitude = '106.7') => {
    mode.value = 'create';
    form.value = { ...form.value, latitude, longitude };
    await nextTick();
  };
  return { form, mode, state, point };
}
function deferred() {
  let resolve!: (value: StationAddressResult) => void;
  let reject!: (reason: Error) => void;
  const promise = new Promise<StationAddressResult>((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
async function lookup() {
  await vi.advanceTimersByTimeAsync(400);
  await nextTick();
}
function workspace() {
  return scoped(() =>
    useStationWorkspace({
      focusLocation: vi.fn(),
      onPickStart: vi.fn(),
      onPickEnd: vi.fn(),
      onToast: vi.fn(),
    }),
  );
}
function drawer(extra: Record<string, unknown> = {}) {
  const onSave = vi.fn(async () => {}),
    onRetryAddressLookup = vi.fn(),
    onApplyAddressSuggestion = vi.fn();
  const wrapper = mount(StationDrawer, {
    props: {
      station: null,
      mode: 'create',
      form: { ...EMPTY_STATION_FORM, name: 'Trạm mới', latitude: '10.8', longitude: '106.7' },
      saving: false,
      pickingLocation: false,
      onClose: vi.fn(),
      onBeginEdit: vi.fn(),
      onPickLocation: vi.fn(),
      onFieldChange: vi.fn(),
      onSave,
      onRequestDeactivate: vi.fn(),
      onRetryAddressLookup,
      onApplyAddressSuggestion,
      ...extra,
    },
  });
  unmounts.push(() => wrapper.unmount());
  return { wrapper, onSave, onRetryAddressLookup, onApplyAddressSuggestion };
}

beforeEach(() => {
  vi.useFakeTimers();
  vi.resetAllMocks();
  vi.mocked(api.reverseGeocodeStation).mockResolvedValue(result);
  vi.mocked(api.fetchStations).mockResolvedValue([station]);
  vi.mocked(api.createStation).mockImplementation(async (input) => ({ ...station, ...input }));
  vi.mocked(api.updateStation).mockImplementation(async (_id, input) => ({ ...station, ...input }));
});
afterEach(() => {
  unmounts.splice(0).forEach((unmount) => unmount());
  scopes.splice(0).forEach((scope) => scope.stop());
  vi.useRealTimers();
});

test('debounces changing coordinates and fills address without moving the point', async () => {
  const { point, form, state } = setup();
  await point();
  expect(state.addressLookupLoading.value).toBe(true);
  await vi.advanceTimersByTimeAsync(300);
  await point('10.9', '106.8');
  await vi.advanceTimersByTimeAsync(399);
  expect(api.reverseGeocodeStation).not.toHaveBeenCalled();
  await vi.advanceTimersByTimeAsync(1);
  expect(api.reverseGeocodeStation).toHaveBeenCalledExactlyOnceWith(
    10.9,
    106.8,
    expect.any(AbortSignal),
  );
  expect(form.value).toMatchObject({
    address: result.address,
    latitude: '10.9',
    longitude: '106.8',
  });
  expect(state.addressDistanceMeters.value).toBe(12.5);
  expect(state.addressLookupLoading.value).toBe(false);
});

test.each([
  ['0', '0'],
  ['-90', '-180'],
  ['90', '180'],
])('accepts boundary/zero coordinates %s,%s', async (lat, lng) => {
  const { point } = setup();
  await point(lat, lng);
  await lookup();
  expect(api.reverseGeocodeStation).toHaveBeenCalledWith(
    Number(lat),
    Number(lng),
    expect.any(AbortSignal),
  );
});
test.each([
  ['', '106'],
  [' ', '106'],
  ['91', '106'],
  ['10', '-181'],
  ['NaN', '106'],
  ['Infinity', '106'],
])('ignores invalid coordinates %s,%s', async (lat, lng) => {
  const { point, state } = setup();
  await point(lat, lng);
  await lookup();
  expect(api.reverseGeocodeStation).not.toHaveBeenCalled();
  expect(state.addressLookupLoading.value).toBe(false);
});

test('aborts an old lookup and ignores its response even if the provider ignores abort', async () => {
  const old = deferred(),
    current = deferred();
  vi.mocked(api.reverseGeocodeStation)
    .mockReturnValueOnce(old.promise)
    .mockReturnValueOnce(current.promise);
  const { point, state, form } = setup();
  await point();
  await lookup();
  const signal = vi.mocked(api.reverseGeocodeStation).mock.calls[0][2]!;
  await point('11', '107');
  expect(signal.aborted).toBe(true);
  await lookup();
  old.resolve({ address: 'Kết quả cũ', distanceMeters: 99 });
  await Promise.resolve();
  expect(form.value.address).toBe('');
  expect(state.addressLookupLoading.value).toBe(true);
  current.resolve(result);
  await Promise.resolve();
  await nextTick();
  expect(form.value.address).toBe(result.address);
  expect(state.addressDistanceMeters.value).toBe(12.5);
});

test('manual input cancels lookup and subsequent suggestions require explicit acceptance', async () => {
  const pending = deferred();
  vi.mocked(api.reverseGeocodeStation).mockReturnValueOnce(pending.promise);
  const { point, state, form } = setup();
  await point();
  await lookup();
  state.markAddressEdited();
  form.value.address = 'Địa chỉ nhập tay';
  expect(vi.mocked(api.reverseGeocodeStation).mock.calls[0][2]!.aborted).toBe(true);
  pending.resolve(result);
  await Promise.resolve();
  expect(form.value.address).toBe('Địa chỉ nhập tay');
  state.retryAddressLookup();
  await vi.advanceTimersByTimeAsync(0);
  expect(state.addressSuggestion.value).toBe(result.address);
  expect(form.value.address).toBe('Địa chỉ nhập tay');
  state.applyAddressSuggestion();
  expect(form.value.address).toBe(result.address);
});

test('opening edit preserves the saved address; moving the point only suggests a replacement', async () => {
  const { form, mode, state } = setup();
  form.value = { ...form.value, address: station.address!, latitude: '10.8', longitude: '106.7' };
  mode.value = 'edit';
  await nextTick();
  await lookup();
  expect(api.reverseGeocodeStation).not.toHaveBeenCalled();
  form.value.latitude = '11';
  await nextTick();
  await lookup();
  expect(form.value.address).toBe(station.address);
  expect(state.addressSuggestion.value).toBe(result.address);
});

test('moving clears the previous automatic address even if the next lookup fails', async () => {
  const { point, form, state } = setup();
  await point();
  await lookup();
  vi.mocked(api.reverseGeocodeStation).mockRejectedValueOnce(new Error('Dịch vụ đang bận'));
  await point('11', '107');
  expect(form.value.address).toBe('');
  await lookup();
  expect(state.addressLookupError.value).toBe('Dịch vụ đang bận');
  expect(state.addressLookupLoading.value).toBe(false);
  state.retryAddressLookup();
  await vi.advanceTimersByTimeAsync(0);
  expect(form.value.address).toBe(result.address);
  expect(state.addressLookupError.value).toBeNull();
});

test('empty results allow manual input and long suggestions are not truncated or applied', async () => {
  const { point, state, form } = setup();
  vi.mocked(api.reverseGeocodeStation).mockResolvedValueOnce({
    address: null,
    distanceMeters: null,
  });
  await point();
  await lookup();
  expect(state.addressLookupError.value).toContain('nhập thủ công');
  const longAddress = 'a'.repeat(256);
  vi.mocked(api.reverseGeocodeStation).mockResolvedValueOnce({
    address: longAddress,
    distanceMeters: 0,
  });
  state.retryAddressLookup();
  await vi.advanceTimersByTimeAsync(0);
  expect(state.addressSuggestion.value).toBe(longAddress);
  expect(state.addressLookupError.value).toContain('255');
  state.applyAddressSuggestion();
  expect(form.value.address).toBe('');
});

test('closing clears debounce and a reopened identical point rejects responses from the previous session', async () => {
  const { point, state, form, mode } = setup();
  await point();
  mode.value = 'closed';
  await nextTick();
  await lookup();
  expect(api.reverseGeocodeStation).not.toHaveBeenCalled();
  const pending = deferred();
  vi.mocked(api.reverseGeocodeStation).mockReturnValueOnce(pending.promise);
  await point();
  await lookup();
  state.resetAddressLookup();
  mode.value = 'closed';
  await nextTick();
  await point();
  pending.resolve({ address: 'Phiên cũ', distanceMeters: 1 });
  await Promise.resolve();
  expect(form.value.address).toBe('');
  await lookup();
  expect(form.value.address).toBe(result.address);
});

test('scope disposal clears timers and aborts pending HTTP without applying errors', async () => {
  const { point, state } = setup();
  await point();
  scopes[0].stop();
  expect(vi.getTimerCount()).toBe(0);
  expect(state.addressLookupLoading.value).toBe(false);
  const pending = deferred();
  vi.mocked(api.reverseGeocodeStation).mockReturnValueOnce(pending.promise);
  const other = setup();
  await other.point();
  await lookup();
  scopes[1].stop();
  expect(vi.mocked(api.reverseGeocodeStation).mock.calls[0][2]!.aborted).toBe(true);
  pending.reject(new Error('Request bị hủy'));
  await Promise.resolve();
  expect(other.state.addressLookupError.value).toBeNull();
});

test('workspace guards saving until lookup finishes, then persists the filled address', async () => {
  const state = workspace();
  state.handleBeginCreate();
  state.setStationForm({
    ...state.stationForm,
    name: 'Trạm mới',
    latitude: '10.8',
    longitude: '106.7',
  });
  const input: StationInput = {
    name: 'Trạm mới',
    address: null,
    latitude: 10.8,
    longitude: 106.7,
    checkinRadiusMeters: 50,
  };
  await state.handleSaveStation(input);
  expect(api.createStation).not.toHaveBeenCalled();
  await lookup();
  expect(state.stationForm.address).toBe(result.address);
  await state.handleSaveStation({ ...input, address: state.stationForm.address });
  expect(api.createStation).toHaveBeenCalledWith({ ...input, address: result.address });
  expect(state.formMode).toBe('closed');
  expect(state.addressSuggestion).toBeNull();
});

test('workspace address field edits allow saving despite provider failure; edit uses update', async () => {
  const state = workspace();
  state.handleBeginCreate();
  state.setStationForm({ ...state.stationForm, latitude: '10.8', longitude: '106.7' });
  vi.mocked(api.reverseGeocodeStation).mockRejectedValueOnce(new Error('Chưa cấu hình HERE'));
  await lookup();
  expect(state.addressLookupError).toBe('Chưa cấu hình HERE');
  state.handleFieldChange('address', 'Địa chỉ tự nhập');
  expect(state.addressLookupError).toBeNull();
  await state.handleSaveStation({ ...station, address: 'Địa chỉ tự nhập' });
  expect(api.createStation).toHaveBeenCalledWith(
    expect.objectContaining({ address: 'Địa chỉ tự nhập' }),
  );
  state.handleBeginEdit(station);
  await nextTick();
  await lookup();
  expect(state.stationForm.address).toBe(station.address);
  await state.handleSaveStation(station);
  expect(api.updateStation).toHaveBeenCalledWith(1, station);
});

test('drawer displays lookup progress and blocks both submit button and direct form submit', async () => {
  const { wrapper, onSave } = drawer({ addressLookupLoading: true });
  expect(wrapper.get('[role="status"]').text()).toContain('Đang lấy địa chỉ');
  expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeDefined();
  await wrapper.get('form').trigger('submit');
  expect(onSave).not.toHaveBeenCalled();
  await wrapper.setProps({
    addressLookupLoading: false,
    addressLookupError: 'Không kết nối được HERE',
  });
  expect(wrapper.get('[role="alert"]').text()).toBe('Không kết nối được HERE');
  expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined();
  await wrapper.get('form').trigger('submit');
  expect(onSave).toHaveBeenCalledOnce();
});

test('drawer hides supplementary information before lookup and after filling the address', async () => {
  const { wrapper } = drawer();
  expect(wrapper.find('.address-lookup').exists()).toBe(false);
  await wrapper.setProps({
    addressSuggestion: result.address,
    form: {
      ...EMPTY_STATION_FORM,
      name: 'Trạm mới',
      latitude: '10.8',
      longitude: '106.7',
      address: result.address,
    },
  });
  expect(wrapper.find('.address-lookup').exists()).toBe(false);
  expect(wrapper.get<HTMLInputElement>('input[maxlength="255"]').element.value).toBe(
    result.address,
  );
  expect(wrapper.text()).not.toContain('Đã tìm được địa chỉ gần điểm đã chọn');
  expect(wrapper.text()).not.toContain('Địa chỉ cách điểm đã chọn');
  expect(wrapper.text()).not.toContain('Lấy địa chỉ từ tọa độ');
});

test('drawer still offers explicit acceptance when the suggestion differs from the manual address', async () => {
  const { wrapper, onApplyAddressSuggestion } = drawer({ addressSuggestion: result.address });
  expect(wrapper.get('.address-suggestion').text()).toContain(result.address);
  expect(wrapper.findAll('.address-lookup-actions button')).toHaveLength(1);
  await wrapper.get('.address-lookup-actions button').trigger('click');
  expect(onApplyAddressSuggestion).toHaveBeenCalledOnce();
});

test('drawer retains retry and explicit suggestion actions on lookup errors', async () => {
  const { wrapper, onRetryAddressLookup, onApplyAddressSuggestion } = drawer({
    addressLookupError: 'Dịch vụ đang bận',
    addressSuggestion: result.address,
  });
  expect(wrapper.find('.address-distance').exists()).toBe(false);
  await wrapper.get('.address-lookup-actions button:first-child').trigger('click');
  await wrapper.get('.address-lookup-actions button:last-child').trigger('click');
  expect(onRetryAddressLookup).toHaveBeenCalledOnce();
  expect(onApplyAddressSuggestion).toHaveBeenCalledOnce();
  await wrapper.setProps({ addressSuggestion: 'a'.repeat(256) });
  expect(
    wrapper.get('.address-lookup-actions button:last-child').attributes('disabled'),
  ).toBeDefined();
  await wrapper.setProps({ form: { ...EMPTY_STATION_FORM, latitude: '91', longitude: '106' } });
  expect(
    wrapper.get('.address-lookup-actions button:first-child').attributes('disabled'),
  ).toBeDefined();
});
