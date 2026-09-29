import { onScopeDispose, ref, watch, type Ref } from 'vue';
import { reverseGeocodeStation } from '@/features/stations/api/stations';
import type { StationFormMode, StationFormState } from '@/features/stations/types/station';

function parseCoordinates(latitudeText: string, longitudeText: string) {
  if (!latitudeText.trim() || !longitudeText.trim()) {
    return null;
  }

  const latitude = Number(latitudeText);
  const longitude = Number(longitudeText);

  if (
    !Number.isFinite(latitude) ||
    !Number.isFinite(longitude) ||
    latitude < -90 ||
    latitude > 90 ||
    longitude < -180 ||
    longitude > 180
  ) {
    return null;
  }
  return { latitude, longitude };
}

export function useStationGeocoding(form: Ref<StationFormState>, mode: Ref<StationFormMode>) {
  const addressLookupLoading = ref(false);
  const addressLookupError = ref<string | null>(null);
  const addressSuggestion = ref<string | null>(null);
  const addressDistanceMeters = ref<number | null>(null);

  let alive = true;
  let version = 0;
  let timer: ReturnType<typeof setTimeout> | null = null;
  let controller: AbortController | null = null;

  let manuallyEdited = false;
  let lastAutomaticAddress: string | null = null;

  function cancelPendingLookup() {
    version += 1;

    if (timer !== null) {
      clearTimeout(timer);
      timer = null;
    }

    controller?.abort();
    controller = null;

    addressLookupLoading.value = false;
  }

  function clearLookupResult() {
    addressLookupError.value = null;
    addressSuggestion.value = null;
    addressDistanceMeters.value = null;
  }

  function resetAddressLookup(preserveManualAddress = false) {
    cancelPendingLookup();
    clearLookupResult();

    manuallyEdited = preserveManualAddress;
    lastAutomaticAddress = null;
  }

  function markAddressEdited() {
    cancelPendingLookup();
    clearLookupResult();

    manuallyEdited = true;
    lastAutomaticAddress = null;
  }

  function applyAddressSuggestion() {
    const address = addressSuggestion.value;

    if (!address || mode.value === 'closed') {
      return;
    }

    if (address.length > 255) {
      addressLookupError.value = 'Địa chỉ gợi ý dài hơn 255 ký tự. Hãy nhập phiên bản rút gọn.';
      return;
    }

    manuallyEdited = false;
    lastAutomaticAddress = address;
    addressLookupError.value = null;

    form.value = {
      ...form.value,
      address,
    };
  }

  async function lookup(
    latitudeText: string,
    longitudeText: string,
    expectedVersion: number,
    expectedMode: StationFormMode,
  ) {
    const coordinates = parseCoordinates(latitudeText, longitudeText);

    const isCurrent = () =>
      alive &&
      version === expectedVersion &&
      mode.value === expectedMode &&
      mode.value !== 'closed' &&
      form.value.latitude === latitudeText &&
      form.value.longitude === longitudeText;

    if (!coordinates || !isCurrent()) {
      return;
    }

    const requestController = new AbortController();
    controller = requestController;

    try {
      const result = await reverseGeocodeStation(
        coordinates.latitude,
        coordinates.longitude,
        requestController.signal,
      );

      if (!isCurrent()) {
        return;
      }

      addressSuggestion.value = result.address;
      addressDistanceMeters.value = result.distanceMeters;

      if (!result.address) {
        addressLookupError.value = 'Chưa tìm được địa chỉ tại điểm này. Bạn có thể nhập thủ công.';
        return;
      }

      if (result.address.length > 255) {
        addressLookupError.value = 'Địa chỉ gợi ý dài hơn 255 ký tự. Hãy nhập phiên bản rút gọn.';
        return;
      }

      if (!manuallyEdited) {
        applyAddressSuggestion();
      }
    } catch (error: unknown) {
      if (!isCurrent() || requestController.signal.aborted) {
        return;
      }

      addressLookupError.value =
        error instanceof Error
          ? error.message
          : 'Không lấy được địa chỉ. Bạn có thể nhập thủ công.';
    } finally {
      if (alive && version === expectedVersion) {
        addressLookupLoading.value = false;

        if (controller === requestController) {
          controller = null;
        }
      }
    }
  }

  function queueLookup(delayMs = 400) {
    cancelPendingLookup();
    clearLookupResult();

    const latitudeText = form.value.latitude;
    const longitudeText = form.value.longitude;

    if (!alive || mode.value === 'closed' || !parseCoordinates(latitudeText, longitudeText)) {
      return;
    }

    const expectedVersion = version;
    const expectedMode = mode.value;

    addressLookupLoading.value = true;
    timer = setTimeout(() => {
      timer = null;

      void lookup(latitudeText, longitudeText, expectedVersion, expectedMode);
    }, delayMs);
  }

  watch(
    [mode, () => form.value.latitude, () => form.value.longitude],
    ([currentMode], [previousMode]) => {
      if (currentMode === 'closed') {
        resetAddressLookup();
        return;
      }

      if (currentMode !== previousMode) {
        resetAddressLookup(Boolean(form.value.address.trim()));
        // Opening an edit form must not replace its persisted address.
        if (currentMode === 'edit') return;
      } else if (lastAutomaticAddress !== null) {
        // Never save an automatically resolved address for the previous point.
        if (form.value.address === lastAutomaticAddress) {
          form.value = { ...form.value, address: '' };
        }
        lastAutomaticAddress = null;
      }

      queueLookup();
    },
  );

  onScopeDispose(() => {
    alive = false;
    cancelPendingLookup();
  });

  return {
    addressLookupLoading,
    addressLookupError,
    addressSuggestion,
    addressDistanceMeters,
    resetAddressLookup,
    markAddressEdited,
    applyAddressSuggestion,
    retryAddressLookup: () => queueLookup(0),
  };
}
