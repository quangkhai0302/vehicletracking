export interface Station {
  id: number;
  name: string;
  address: string | null;
  latitude: number;
  longitude: number;
  checkinRadiusMeters: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface StationInput {
  name: string;
  address: string | null;
  latitude: number;
  longitude: number;
  checkinRadiusMeters: number;
}

export type StationFormMode = 'closed' | 'create' | 'edit';

export interface StationFormState {
  name: string;
  address: string;
  latitude: string;
  longitude: string;
  checkinRadiusMeters: string;
}

export const EMPTY_STATION_FORM: StationFormState = {
  name: '',
  address: '',
  latitude: '',
  longitude: '',
  checkinRadiusMeters: '50',
};
