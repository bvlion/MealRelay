'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const {
  InvalidFirebaseInstallationRequestError,
  parseFirebaseInstallationRequest,
} = require('../src/firebaseInstallationRequest');

test('parses a valid Firebase Installation ID', () => {
  assert.deepEqual(parseFirebaseInstallationRequest({ fid: 'fid-current' }), { fid: 'fid-current' });
});

test('rejects missing, non-string, and blank Firebase Installation IDs', () => {
  for (const body of [undefined, null, {}, { fid: 123 }, { fid: '  ' }]) {
    assert.throws(
      () => parseFirebaseInstallationRequest(body),
      InvalidFirebaseInstallationRequestError,
    );
  }
});
