import { Client } from '../../../../vehicletracking-frontend/node_modules/@stomp/stompjs/esm6/index.js';

let messageReceived = false;

const client = new Client({
  brokerURL: 'ws://localhost:8080/ws-raw',
  debug: function (str) {
    console.log('[STOMP-DEBUG]', str);
  },
  onConnect: () => {
    console.log('[STOMP-CONNECTED] Connected to ws://localhost:8080/ws-raw');

    // 1. Subscribe to /topic/checkins to verify delivery across subscribers
    client.subscribe('/topic/checkins', (message) => {
      messageReceived = true;
      console.log('[STOMP-SUBSCRIBER-CONFIRMATION] Received CheckInEvent from /topic/checkins:');
      console.log(message.body);
    });

    // 2. Foreign payload with tripId: 999999
    const foreignPayload = {
      tripId: 999999,
      tripCode: 'TRIP-FOREIGN-999999',
      vehicleId: 999,
      plateNumber: '99X-99999',
      stationId: 999,
      stationName: 'Trạm Ngoại Tuyến 999',
      stopOrder: 99,
      checkInTime: new Date().toISOString(),
      message: 'Xe 99X-99999 đã check-in thành công tại Trạm Ngoại Tuyến 999'
    };

    console.log('[STOMP-PUBLISH] Publishing foreign CheckInEvent to /topic/checkins:');
    console.log(JSON.stringify(foreignPayload, null, 2));

    client.publish({
      destination: '/topic/checkins',
      body: JSON.stringify(foreignPayload)
    });

    setTimeout(() => {
      if (messageReceived) {
        console.log('[STOMP-DELIVERY-VERIFIED] Broker broadcast confirmed: message delivered to /topic/checkins subscriber.');
      } else {
        console.warn('[STOMP-WARNING] Subscriber did not receive message before timeout.');
      }
      console.log('[STOMP-DISCONNECT] Finished stimulus execution. Disconnecting...');
      client.deactivate();
      process.exit(messageReceived ? 0 : 1);
    }, 1500);
  },
  onStompError: (frame) => {
    console.error('[STOMP-ERROR] Broker reported error: ' + frame.headers['message']);
    console.error('[STOMP-ERROR] Additional details: ' + frame.body);
    process.exit(1);
  }
});

console.log('[STOMP-INIT] Activating STOMP stimulus client...');
client.activate();
