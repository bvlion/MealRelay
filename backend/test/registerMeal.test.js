'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const { FirestoreMealRepository } = require('../src/firestoreMealRepository');
const { GoogleHealthPendingError } = require('../src/googleHealthNutrition');
const { completeMealRegistration, registerMeal } = require('../src/registerMeal');

const SERVER_GOOGLE_HEALTH_NAME =
  'users/health-user/dataTypes/nutrition-log/dataPoints/server-generated-1';

function registrationFixture({ route = 'image', mealId = 'photo-1',
  occurredAt = '2026-01-01T12:00:00Z', analysis = { foodDisplayName: 'Rice' },
  createOperation = () => ({
    done: true,
    promise: async () => [{ name: SERVER_GOOGLE_HEALTH_NAME }],
  }) } = {}) {
  const documents = new Map();
  const firestore = {
    collection: () => ({ doc: (id) => ({
      id,
      get: async () => ({
        exists: documents.has(id),
        get: (field) => documents.get(id)?.[field],
      }),
      update: async (fields) => { documents.set(id, { ...documents.get(id), ...fields }); },
    }) }),
    runTransaction: async (callback) => callback({
      get: async (reference) => ({
        exists: documents.has(reference.id),
        get: (field) => documents.get(reference.id)?.[field],
      }),
      create: (reference, fields) => { documents.set(reference.id, fields); },
      update: (reference, fields) => { documents.set(reference.id, { ...documents.get(reference.id), ...fields }); },
      delete: (reference) => { documents.delete(reference.id); },
    }),
  };
  const calls = { create: [], credentials: [] };
  const healthClient = {
    createDataPoint: async (request) => {
      calls.create.push(request);
      return [await createOperation(request, calls.create.length)];
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

test('a reservation owner cannot release or finalize a newer reservation', async () => {
  const { args, documents } = registrationFixture();
  const repository = args.mealRepository;

  const first = await repository.reserve('user1', 'photo-1', 'request-1');
  assert.equal(first.type, 'acquired');
  assert.deepEqual(await repository.reserve('user1', 'photo-1', 'request-1'), { type: 'active' });
  assert.deepEqual(await repository.reserve('user1', 'photo-1', 'request-2'), { type: 'different' });

  const [documentId, document] = documents.entries().next().value;
  documents.set(documentId, { ...document, analysisStartedAt: new Date(0).toISOString() });
  const second = await repository.reserve('user1', 'photo-1', 'request-1');
  assert.equal(second.type, 'acquired');
  assert.notEqual(second.reservationId, first.reservationId);

  await repository.releaseReservation(first);
  assert.equal(await repository.saveReserved({ userId: 'user1', mealId: 'photo-1' }, first), false);
  assert.equal(await repository.saveReserved({ userId: 'user1', mealId: 'photo-1' }, second), true);
});

function savedMeal(documents) {
  return [...documents.values()][0];
}

async function registerReserved(args, requestHash = 'request-1') {
  const reservation = await args.mealRepository.reserve('user1', args.mealId, requestHash);
  assert.equal(reservation.type, 'acquired');
  return registerMeal({ ...args, reservation });
}

function completeSaved(args, record, status) {
  return completeMealRegistration({
    record,
    status,
    authRepository: args.authRepository,
    clientId: args.clientId,
    clientSecret: args.clientSecret,
    createOAuthClient: args.createOAuthClient,
    healthClient: args.healthClient,
    mealRepository: args.mealRepository,
  });
}

test('registration uses token owner credentials and remains idempotent on retry', async () => {
  const { args, calls, documents } = registrationFixture({
    analysis: { foodDisplayName: 'Rice', estimated: { energyKcal: 200 } },
  });

  const first = await registerReserved(args);
  const second = await completeSaved(args, first.record, 'registered');

  assert.equal(first.isAlreadyRegistered, false);
  assert.equal(second.isAlreadyRegistered, true);
  assert.equal(calls.create.length, 1);
  assert.equal(calls.create[0].dataPoint.name, undefined);
  assert.equal(savedMeal(documents).status, 'registered');
  assert.equal(savedMeal(documents).googleHealthName, SERVER_GOOGLE_HEALTH_NAME);
  assert.deepEqual(calls.credentials, [{ refresh_token: 'refresh-1' }]);
  assert.equal(first.record.userId, 'user1');
  assert.equal(documents.size, 1);
  assert.equal(savedMeal(documents).status, 'registered');
  assert.notEqual(savedMeal(documents).submittedAt, first.record.eatenAt);
  assert.deepEqual(await args.mealRepository.find('user1', 'photo-1'), {
    record: first.record,
    status: 'registered',
  });

  await assert.rejects(registerMeal({ ...args, authorization: 'Bearer invalid', reservation: {} }),
    /authentication failed/);
  assert.equal(calls.create.length, 1);
});

test('a pending operation becomes registered only after Google Health reports success', async () => {
  let completeOperation;
  const { args, calls, documents } = registrationFixture({
    route: 'text', mealId: 'text-1', occurredAt: '2026-01-03T20:00:00+09:00',
    analysis: { foodDisplayName: 'Toast', eatenAt: '2026-01-01T08:00:00+09:00' },
    createOperation: () => ({
      done: false,
      promise: () => new Promise((resolve) => {
        completeOperation = () => resolve([{ name: SERVER_GOOGLE_HEALTH_NAME }]);
      }),
    }),
  });

  const registration = registerReserved(args);
  await new Promise(setImmediate);
  assert.equal(savedMeal(documents).status, 'pending');
  completeOperation();
  const result = await registration;
  await completeSaved(args, result.record, 'registered');

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

  await assert.rejects(registerReserved(args), /Creation failed/);

  assert.equal(savedMeal(documents).status, 'pending');
  assert.equal(calls.create.length, 1);

  const record = savedMeal(documents).record;
  await assert.rejects(completeSaved(args, record, 'pending'), GoogleHealthPendingError);
  assert.equal(calls.create.length, 1);
});

