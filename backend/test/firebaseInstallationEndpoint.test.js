'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const { handleFirebaseInstallationRequest } = require('../src/firebaseInstallationEndpoint');

function responseFixture() {
  return {
    headers: {},
    set(name, value) { this.headers[name] = value; return this; },
    status(value) { this.statusCode = value; return this; },
    json(value) { this.body = value; return this; },
    end() { this.isEnded = true; return this; },
  };
}

test('associates the current FID with the authenticated MealRelay user', async () => {
  const requests = [];
  const response = responseFixture();
  await handleFirebaseInstallationRequest({
    request: {
      method: 'PUT',
      is: () => true,
      get: () => 'Bearer valid',
      body: { fid: 'fid-current' },
    },
    response,
    authRepository: { findSubByToken: async (token) => token === 'valid' ? 'user1' : null },
    installationRepository: { register: async (request) => requests.push(request) },
  });

  assert.equal(response.statusCode, 204);
  assert.equal(response.headers['Cache-Control'], 'no-store');
  assert.deepEqual(requests, [{ sub: 'user1', fid: 'fid-current' }]);
});

test('rejects Firebase installation registration without MealRelay authentication', async () => {
  const response = responseFixture();
  await handleFirebaseInstallationRequest({
    request: { method: 'PUT', is: () => true, get: () => 'Bearer invalid', body: { fid: 'fid' } },
    response,
    authRepository: { findSubByToken: async () => null },
    installationRepository: { register: async () => assert.fail('Unauthenticated registration is rejected') },
  });

  assert.equal(response.statusCode, 401);
});

test('rejects an invalid Firebase Installation request before persistence', async () => {
  const response = responseFixture();
  await handleFirebaseInstallationRequest({
    request: { method: 'PUT', is: () => true, get: () => 'Bearer valid', body: { fid: '  ' } },
    response,
    authRepository: { findSubByToken: async () => 'user1' },
    installationRepository: { register: async () => assert.fail('Invalid request is rejected') },
  });

  assert.equal(response.statusCode, 400);
  assert.deepEqual(response.body, { error: 'Firebase Installation ID is invalid' });
});
