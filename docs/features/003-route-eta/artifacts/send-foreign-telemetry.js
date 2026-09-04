// Standalone external test script for TC-007 Trip Isolation verification
// Runs in Node.js (Node 22+) using native WebSocket.
// Does NOT touch or modify any production application bundle or source code.
// Destination: ws://localhost:8080/ws-raw -> /topic/telemetry

const brokerUrl = process.env.WS_URL || 'ws://localhost:8080/ws-raw';
console.log(`[${new Date().toISOString()}] Connecting to STOMP broker at ${brokerUrl}...`);

const ws = new WebSocket(brokerUrl);

ws.onopen = () => {
  console.log(`[${new Date().toISOString()}] WebSocket connection opened. Sending STOMP CONNECT frame...`);
  ws.send("CONNECT\naccept-version:1.2,1.1,1.0\nheart-beat:10000,10000\n\n\0");
};

ws.onmessage = (event) => {
  const message = event.data.toString();
  if (message.startsWith('CONNECTED')) {
    console.log(`[${new Date().toISOString()}] Received STOMP CONNECTED frame from broker.`);

    const foreignPayload = {
      vehicleId: 999,
      plateNumber: '99X-99999',
      tripId: 99999,
      tripCode: 'TRIP-99999',
      tripStatus: 'RUNNING',
      routeId: 999,
      routeName: 'Tuyến Ngoại Tuyến (Foreign Route)',
      latitude: 10.5000,
      longitude: 106.5000,
      speed: 99.0,
      heading: 180.0,
      status: 'IN_TRANSIT',
      currentStopIndex: 1,
      targetStationId: 999,
      targetStationName: 'Trạm Ngoại Tuyến',
      distanceToTargetMeters: 500.0,
      etaSecondsToTarget: 120,
      etaSecondsToCompletion: 300,
      estimatedCompletionTime: new Date(Date.now() + 300000).toISOString(),
      stationsEta: [],
      inIncidentZone: false,
      timestamp: new Date().toISOString()
    };

    const body = JSON.stringify(foreignPayload);
    const sendFrame = `SEND\ndestination:/topic/telemetry\ncontent-type:application/json\n\n${body}\0`;

    console.log(`[${new Date().toISOString()}] Dispatching foreign trip telemetry (tripId: ${foreignPayload.tripId}, plateNumber: ${foreignPayload.plateNumber}, speed: ${foreignPayload.speed} km/h)...`);
    ws.send(sendFrame);
    console.log(`[${new Date().toISOString()}] Successfully sent foreign telemetry frame to /topic/telemetry.`);

    setTimeout(() => {
      console.log(`[${new Date().toISOString()}] Closing WebSocket connection cleanly.`);
      ws.close();
      console.log(`[${new Date().toISOString()}] Test script completed successfully with exit code 0.`);
      process.exit(0);
    }, 500);
  }
};

ws.onerror = (err) => {
  console.error(`[${new Date().toISOString()}] WebSocket error:`, err);
  process.exit(1);
};
