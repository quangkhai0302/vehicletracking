import { Client } from '@stomp/stompjs';
import { VehicleTelemetry, CheckInEvent, AlertMessage } from '../types';

export class WebSocketService {
  private client: Client | null = null;
  public connected: boolean = false;
  private subscribers: {
    telemetry: ((data: VehicleTelemetry) => void)[];
    checkins: ((data: CheckInEvent) => void)[];
    alerts: ((data: AlertMessage) => void)[];
  } = {
    telemetry: [],
    checkins: [],
    alerts: []
  };

  connect(onConnectSuccess?: () => void) {
    if (this.client && this.connected) return;

    this.client = new Client({
      // Backend cung cấp endpoint WebSocket thuần tại /ws-raw.
      // Dùng WebSocket native để tránh sockjs-client (CommonJS) truy cập
      // biến Node `global` trong môi trường trình duyệt.
      brokerURL: 'ws://localhost:8080/ws-raw',
      reconnectDelay: 3000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    this.client.onConnect = (frame) => {
      this.connected = true;
      console.log('STOMP Connected successfully:', frame);

      // Subscribe kênh vị trí xe Realtime
      this.client?.subscribe('/topic/telemetry', (message) => {
        if (message.body) {
          const data: VehicleTelemetry = JSON.parse(message.body);
          this.subscribers.telemetry.forEach(cb => cb(data));
        }
      });

      // Subscribe kênh Auto Check-in tại các trạm
      this.client?.subscribe('/topic/checkins', (message) => {
        if (message.body) {
          const data: CheckInEvent = JSON.parse(message.body);
          this.subscribers.checkins.forEach(cb => cb(data));
        }
      });

      // Subscribe kênh Cảnh báo kẹt xe & thay đổi lịch trình
      this.client?.subscribe('/topic/alerts', (message) => {
        if (message.body) {
          const data: AlertMessage = JSON.parse(message.body);
          this.subscribers.alerts.forEach(cb => cb(data));
        }
      });

      if (onConnectSuccess) onConnectSuccess();
    };

    this.client.onStompError = (frame) => {
      console.error('Broker reported error: ' + frame.headers['message']);
      console.error('Additional details: ' + frame.body);
    };

    this.client.onWebSocketClose = () => {
      this.connected = false;
    };

    this.client.activate();
  }

  onTelemetry(callback: (data: VehicleTelemetry) => void) {
    this.subscribers.telemetry.push(callback);
    return () => {
      this.subscribers.telemetry = this.subscribers.telemetry.filter(cb => cb !== callback);
    };
  }

  onCheckIn(callback: (data: CheckInEvent) => void) {
    this.subscribers.checkins.push(callback);
    return () => {
      this.subscribers.checkins = this.subscribers.checkins.filter(cb => cb !== callback);
    };
  }

  onAlert(callback: (data: AlertMessage) => void) {
    this.subscribers.alerts.push(callback);
    return () => {
      this.subscribers.alerts = this.subscribers.alerts.filter(cb => cb !== callback);
    };
  }

  disconnect() {
    if (this.client) {
      this.client.deactivate();
      this.connected = false;
    }
  }
}

export const wsService = new WebSocketService();
