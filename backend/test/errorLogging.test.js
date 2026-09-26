'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const { logBackendError } = require('../src/errorLogging');

test('structured error logging keeps diagnostic metadata without raw messages or response bodies', () => {
  const cause = new Error('raw upstream message must not be logged');
  cause.code = 7;
  cause.type = 'upstream_type';
  cause.request_id = 'request-123';
  cause.response = {
    status: 403,
    data: {
      error: {
        code: 'permission_denied',
        type: 'authorization_error',
        message: 'raw response body must not be logged',
      },
    },
  };
  const error = new Error('outer raw message must not be logged', { cause });
  error.status = 502;

  const logged = [];
  const originalConsoleError = console.error;
  console.error = (value) => logged.push(value);
  try {
    logBackendError({
      message: 'Text meal request failed',
      error,
      responseStatus: 502,
      reason: 'Meal text analysis failed',
    });
  } finally {
    console.error = originalConsoleError;
  }

  assert.equal(logged.length, 1);
  const entry = JSON.parse(logged[0]);
  assert.deepEqual(entry, {
    severity: 'ERROR',
    message: 'Text meal request failed',
    responseStatus: 502,
    reason: 'Meal text analysis failed',
    errorName: 'Error',
    status: 502,
    causeErrorName: 'Error',
    causeErrorCode: 7,
    causeErrorType: 'upstream_type',
    causeRequestId: 'request-123',
    causeUpstreamStatus: 403,
    causeUpstreamErrorCode: 'permission_denied',
    causeUpstreamErrorType: 'authorization_error',
  });
  assert.doesNotMatch(logged[0], /raw upstream message|raw response body|outer raw message/);
});

test('warning logs are emitted as structured JSON', () => {
  const logged = [];
  const originalConsoleWarn = console.warn;
  console.warn = (value) => logged.push(value);
  try {
    logBackendError({
      message: 'Request rejected',
      error: Object.assign(new Error('do not log this'), { status: 400 }),
      responseStatus: 400,
      reason: 'Invalid request',
      severity: 'WARNING',
    });
  } finally {
    console.warn = originalConsoleWarn;
  }

  assert.deepEqual(JSON.parse(logged[0]), {
    severity: 'WARNING',
    message: 'Request rejected',
    responseStatus: 400,
    reason: 'Invalid request',
    errorName: 'Error',
    status: 400,
  });
});
