import { Client } from '../../../../vehicletracking-frontend/node_modules/@stomp/stompjs/esm6/index.js';

const mode = process.argv[2] || 'tc009'; // 'tc009' | 'tc012' | 'foreign' | 'wrong-run' | 'stale-seq' | 'valid-seq' | 'malformed'
const targetTripId = parseInt(process.argv[3] || '1', 10);
const targetRunId = process.argv[4] || null; // if not provided, will query or use default
const currentSeq = parseInt(process.argv[5] || '7', 10);

const client = new Client({
  brokerURL: 'ws://localhost:8080/ws-raw',
  debug: function (str) {
    // console.log('[STOMP-DEBUG]', str);
  },
  onConnect: async () => {
    console.log('[STOMP-CONNECTED] Connected to ws://localhost:8080/ws-raw');

    let activeRunId = targetRunId;
    let actualLastSeq = currentSeq;

    // Lấy status thực tế từ backend nếu chưa có runId
    if (!activeRunId) {
      try {
        const res = await fetch(`http://localhost:8080/api/simulator/status/${targetTripId}`);
        if (res.ok) {
          const status = await res.json();
          if (status.simulationRunId) {
            activeRunId = status.simulationRunId;
            actualLastSeq = status.lastPublishedSequence || currentSeq;
            console.log(`[STOMP-INIT] Retrieved active simulation status: runId=${activeRunId}, lastSequence=${actualLastSeq}`);
          }
        }
      } catch (err) {
        console.warn('[STOMP-INIT] Could not fetch simulator status, using fallback values.');
      }
    }

    if (!activeRunId) {
      activeRunId = '11111111-2222-3333-4444-555555555555';
    }

    let receivedCount = 0;
    client.subscribe('/topic/telemetry', (message) => {
      receivedCount++;
      console.log(`[STOMP-SUBSCRIBER] Message #${receivedCount} received on /topic/telemetry:`);
      try {
        const parsed = JSON.parse(message.body);
        console.log(`  tripId=${parsed.tripId}, runId=${parsed.simulationRunId}, seq=${parsed.sequence}, speed=${parsed.speed}`);
      } catch {
        console.log(`  [NON-JSON BODY]: ${message.body}`);
      }
    });

    const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

    const buildTelemetry = (trip, run, seq, lat, lng, spd) => ({
      vehicleId: 1,
      plateNumber: '51B-299.88',
      tripId: trip,
      tripCode: `TRIP-${trip}`,
      tripStatus: 'RUNNING',
      routeId: 1,
      routeName: 'Tuyến số 01: BX Miền Đông - Chợ Bến Thành',
      latitude: lat,
      longitude: lng,
      speed: spd,
      heading: 45.0,
      status: 'IN_TRANSIT',
      currentStopIndex: 1,
      targetStationId: 2,
      targetStationName: 'Ngã tư Hàng Xanh',
      distanceToTargetMeters: 450.0,
      etaSecondsToTarget: 60,
      etaSecondsToCompletion: 300,
      estimatedCompletionTime: new Date(Date.now() + 300000).toISOString(),
      stationsEta: [],
      inIncidentZone: false,
      timestamp: new Date().toISOString(),
      simulationRunId: run,
      sequence: seq
    });

    if (mode === 'tc009') {
      console.log('\n--- BẮT ĐẦU TC-009: KIỂM THỬ BỘ LỌC TELEMETRY (FOREIGN, WRONG RUN, STALE SEQ, VALID SEQ) ---');

      // 1. Foreign Trip (tripId: 999999)
      console.log('\n[TC-009 Step 1] Phát telemetry của foreign Trip (tripId: 999999)...');
      const foreignPayload = buildTelemetry(999999, activeRunId, actualLastSeq + 1, 10.9999, 106.9999, 99.0);
      client.publish({ destination: '/topic/telemetry', body: JSON.stringify(foreignPayload) });
      await sleep(1000);

      // 2. Wrong simulationRunId
      console.log('\n[TC-009 Step 2] Phát telemetry với simulationRunId ngoại lai (00000000-0000-0000-0000-000000000000)...');
      const wrongRunPayload = buildTelemetry(targetTripId, '00000000-0000-0000-0000-000000000000', actualLastSeq + 1, 10.9999, 106.9999, 99.0);
      client.publish({ destination: '/topic/telemetry', body: JSON.stringify(wrongRunPayload) });
      await sleep(1000);

      // 3. Stale sequence (sequence <= lastSequence)
      console.log(`\n[TC-009 Step 3] Phát telemetry với sequence cũ/trùng lặp (sequence: ${actualLastSeq})...`);
      const staleSeqPayload = buildTelemetry(targetTripId, activeRunId, actualLastSeq, 10.9999, 106.9999, 99.0);
      client.publish({ destination: '/topic/telemetry', body: JSON.stringify(staleSeqPayload) });
      await sleep(1000);

      // 4. Valid subsequent sequence (sequence: actualLastSeq + 1)
      console.log(`\n[TC-009 Step 4] Phát telemetry hợp lệ tiếp theo (sequence: ${actualLastSeq + 1}, speed: 38.5 km/h)...`);
      const validSeqPayload = buildTelemetry(targetTripId, activeRunId, actualLastSeq + 1, 10.8016, 106.7114, 38.5);
      client.publish({ destination: '/topic/telemetry', body: JSON.stringify(validSeqPayload) });
      await sleep(1500);

      console.log(`\n[TC-009 HOÀN THÀNH] Đã phát 4 bản tin kích thích. Subscriber nhận được: ${receivedCount} bản tin.`);
    } else if (mode === 'tc012') {
      console.log('\n--- BẮT ĐẦU TC-012: KIỂM THỬ BẢN TIN MALFORMED VÀ VALID-JSON WRONG-SHAPE (REV-003) ---');

      // 1. Raw text không phải JSON
      console.log('[TC-012 Step 1] Gửi payload text thuần không phải JSON: "INVALID_STOMP_PAYLOAD_NOT_JSON"...');
      client.publish({ destination: '/topic/telemetry', body: 'INVALID_STOMP_PAYLOAD_NOT_JSON' });
      await sleep(1000);

      // 2. Truncated JSON
      console.log('[TC-012 Step 2] Gửi payload JSON bị cụt/cắt: "{\"tripId\": 1, \"speed\": "...');
      client.publish({ destination: '/topic/telemetry', body: '{"tripId": 1, "speed": ' });
      await sleep(1000);

      // 3. Valid JSON null (REV-003)
      console.log('[TC-012 Step 3] Gửi payload valid JSON null: "null"...');
      client.publish({ destination: '/topic/telemetry', body: 'null' });
      await sleep(1000);

      // 4. Valid JSON array (REV-003)
      console.log('[TC-012 Step 4] Gửi payload valid JSON array: "[1, 2, 3]"...');
      client.publish({ destination: '/topic/telemetry', body: '[1, 2, 3]' });
      await sleep(1000);

      // 5. Valid JSON primitive string (REV-003)
      console.log('[TC-012 Step 5] Gửi payload valid JSON primitive string: "\\"just a string\\""...');
      client.publish({ destination: '/topic/telemetry', body: '"just a string"' });
      await sleep(1000);

      // 6. Valid JSON empty object (REV-003)
      console.log('[TC-012 Step 6] Gửi payload valid JSON empty object: "{}"...');
      client.publish({ destination: '/topic/telemetry', body: '{}' });
      await sleep(1000);

      // 7. Valid JSON missing required coordinates (REV-003)
      console.log('[TC-012 Step 7] Gửi payload valid JSON thiếu tọa độ: "{\"tripId\": 1, \"speed\": 25.0}"...');
      client.publish({ destination: '/topic/telemetry', body: JSON.stringify({ tripId: targetTripId, speed: 25.0 }) });
      await sleep(1000);

      // 8. Payload hợp lệ tiếp theo
      console.log(`[TC-012 Step 8] Gửi payload hợp lệ tiếp theo (sequence: ${actualLastSeq + 1})...`);
      const validPayload = buildTelemetry(targetTripId, activeRunId, actualLastSeq + 1, 10.8016, 106.7114, 42.0);
      client.publish({ destination: '/topic/telemetry', body: JSON.stringify(validPayload) });
      await sleep(1500);

      console.log(`\n[TC-012 HOÀN THÀNH] Đã phát 7 bản tin malformed/wrong-shape và 1 bản tin hợp lệ. Subscriber nhận được: ${receivedCount} bản tin.`);
    }

    client.deactivate();
    process.exit(0);
  },
  onStompError: (frame) => {
    console.error('[STOMP-ERROR] Broker reported error:', frame.headers['message']);
    process.exit(1);
  }
});

client.activate();
