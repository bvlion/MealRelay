'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const { FirestoreMealRepository } = require('../src/firestoreMealRepository');
const { GoogleHealthPendingError } = require('../src/googleHealthNutrition');
const { registerMeal } = require('../src/registerMeal');

function registrationFixture({ route = 'image', mealId = 'photo-1',
  occurredAt = '2026-01-01T12:00:00Z', analysis = { foodDisplayName: 'Rice' },
  createOperation = ({ dataPoint }) => ({
    done: true,
    promise: async () => [{ name: dataPoint.name }],
  }),
  getPoint = () => { throw { code: 5 }; } } = {}) {
  const documents = new Map();
  const firestore = {
    collection: () => ({ doc: (id) => ({
      id,
      update: async (fields) => { documents.set(id, { ...documents.get(id), ...fields }); },
    }) }),
    runTransaction: async (callback) => callback({
      get: async (reference) => ({
        exists: documents.has(reference.id),
        get: (field) => documents.get(reference.id)?.[field],
      }),
      create: (reference, fields) => { documents.set(reference.id, fields); },
    }),
  };
  const calls = { create: [], get: [], credentials: [] };
  const healthClient = {
    createDataPoint: async (request) => {
      calls.create.push(request);
      return [await createOperation(request, calls.create.length)];
    },
    getDataPoint: async (request) => {
      calls.get.push(request);
      return [await getPoint(request)];
    },
  };
  const args = {
    route, authorization: 'Bearer token-1', mealId, occurredAt, analysis,
    authRepository: {
      findSubByToken: async (token) => token === 'token-1' ? 'user1' : null,
      getRefreshToken: async (sub) => sub === 'user1' ? 'refresh-1' : null,
    },
    mealRepository: new FirestoreMealRepository(firestore),
    clientId: 'client', clientSecret: 'secret',
    createOAuthClient: () => ({ setCredentials: (credentials) => calls.credentials.push(credentials) }),
    healthClient,
  };
  return { args, calls, documents };
}

function savedMeal(documents) {
  return [...documents.values()][0];
}

test('registration uses token owner credentials and remains idempotent on retry', async () => {
  const { args, calls, documents } = registrationFixture({
    analysis: { foodDisplayName: 'Rice', estimated: { energyKcal: 200 } },
  });

  const first = await registerMeal(args);
  const second = await registerMeal(args);

  assert.equal(first.isAlreadyRegistered, false);
  assert.equal(second.isAlreadyRegistered, true);
  assert.equal(calls.create.length, 1);
  assert.deepEqual(calls.credentials, [{ refresh_token: 'refresh-1' }]);
  assert.equal(first.record.userId, 'user1');
  assert.equal(documents.size, 1);
  assert.equal(savedMeal(documents).status, 'registered');
  assert.notEqual(savedMeal(documents).submittedAt, first.record.eatenAt);

  await assert.rejects(registerMeal({ ...args, analysis: { foodDisplayName: 'Different meal' } }),
    /Meal ID already has different content/);
  await assert.rejects(registerMeal({ ...args, authorization: 'Bearer invalid' }),
    /authentication failed/);
  assert.equal(calls.create.length, 1);
});

test('a pending operation becomes registered only after Google Health reports success', async () => {
  let completeOperation;
  const { args, calls, documents } = registrationFixture({
    route: 'text', mealId: 'text-1', occurredAt: '2026-01-03T20:00:00+09:00',
    analysis: { foodDisplayName: 'Toast', eatenAt: '2026-01-01T08:00:00+09:00' },
    createOperation: ({ dataPoint }) => ({
      done: false,
      promise: () => new Promise((resolve) => {
        completeOperation = () => resolve([{
          name: dataPoint.name.replace('users/me/', 'users/health-user/'),
        }]);
      }),
    }),
  });

  const registration = registerMeal(args);
  await new Promise(setImmediate);
  assert.equal(savedMeal(documents).status, 'pending');
  completeOperation();
  const result = await registration;
  await registerMeal(args);

  assert.equal(result.record.eatenAt, '2025-12-31T23:00:00.000Z');
  assert.equal(savedMeal(documents).status, 'registered');
  assert.equal(calls.create.length, 1);
});

test('a pending operation that does not create a point is not marked registered', async () => {
  const { args, calls, documents } = registrationFixture({
    createOperation: () => ({
      done: false,
      promise: async () => { throw new Error('Creation failed'); },
    }),
  });

  await assert.rejects(registerMeal(args), /Creation failed/);

  assert.equal(savedMeal(documents).status, 'pending');
  assert.equal(calls.create.length, 1);
});

test('a conflict without a readable point stays pending until the point exists', async () => {
  let isPointVisible = false;
  const { args, calls, documents } = registrationFixture({
    createOperation: (_request, attempt) => {
      if (attempt === 1) throw new Error('Response lost');
      throw { code: 6 };
    },
    getPoint: ({ name }) => {
      if (!isPointVisible) throw { code: 5 };
      return { name: name.replace('users/me/', 'users/health-user/') };
    },
  });

  await assert.rejects(registerMeal(args), /Response lost/);
  await assert.rejects(registerMeal(args), GoogleHealthPendingError);
  assert.equal(savedMeal(documents).status, 'pending');
  isPointVisible = true;
  await registerMeal(args);

  assert.equal(savedMeal(documents).status, 'registered');
  assert.equal(calls.create.length, 2);
});
