// Feature 007 browser gate: reuse the proven operations harness but require
// the live Spring/PostgreSQL run to verify ordered automatic check-ins.
process.env.VERIFICATION_CHECKINS='true';
await import('../../006-telemetry-simulator/verification/live-browser.mjs');
