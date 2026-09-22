'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const { MealRecordError } = require('../src/mealRecord');
const { handleTextMealRequest } = require('../src/textMealEndpoint');

function requestFixture({ text = '朝 トーストとヨーグルト',
  inputAt = '2026-09-21T08:00:00+09:00', mealId = 'text-1' } = {}) {
  const headers = {
    authorization: 'Bearer token-1',
    'content-type': 'application/json',
  };
  return {
    method: 'POST',
    body: { text, inputAt, mealId },
    headers,
    get: (name) => headers[name.toLowerCase()],
    is: (mediaType) => mediaType === 'application/json',
  };
}

function responseFixture() {
  return {
    headers: {},
    statusCode: null,
    body: null,
    set(name, value) { this.headers[name] = value; return this; },
    status(statusCode) { this.statusCode = statusCode; return this; },
    json(body) { this.body = body; return this; },
    end() { return this; },
  };
}

function textAnalysis({ foodDisplayName = 'トーストとヨーグルト', eatenAt = null,
  confirmedEnergyKcal = null, estimatedEnergyKcal = 420, proteinGrams = 12,
  carbohydrateGrams = 35, fatGrams = 8 } = {}) {
  return {
    foodDisplayName,
    eatenAt,
    estimated: { energyKcal: estimatedEnergyKcal, proteinGrams, carbohydrateGrams, fatGrams },
    confirmed: {
      energyKcal: confirmedEnergyKcal === null
        ? null
        : { value: confirmedEnergyKcal, origin: 'userInput' },
      proteinGrams: null,
      carbohydrateGrams: null,
      fatGrams: null,
    },
  };
}

function endpointFixture({ analysisOutputs = [textAnalysis()], healthOutcomes = ['success'] } = {}) {
  const calls = { analysis: [], googleHealth: [], tokenLookups: [] };
  const meals = new Map();
  const reservations = new Map();
  const mealKey = (userId, mealId) => `${userId}:${mealId}`;
  const mealRepository = {
    find: async (userId, mealId) => meals.get(mealKey(userId, mealId)) ?? null,
    reserve: async (userId, mealId, requestHash) => {
      const key = mealKey(userId, mealId);
      const savedMeal = meals.get(key);
      if (savedMeal) {
        return savedMeal.requestHash && savedMeal.requestHash !== requestHash
          ? { type: 'different' }
          : { type: 'saved', savedMeal };
      }
      const activeReservation = reservations.get(key);
      if (activeReservation) {
        return activeReservation.requestHash === requestHash ? { type: 'active' } : { type: 'different' };
      }
      const reservation = { type: 'acquired', userId, mealId, requestHash, reservationId: `reservation-${key}` };
      reservations.set(key, reservation);
      return reservation;
    },
    saveReserved: async (record, reservation) => {
      const key = mealKey(record.userId, record.mealId);
      if (reservations.get(key)?.reservationId !== reservation.reservationId) return false;
      reservations.delete(key);
      meals.set(key, { record, status: 'pending', requestHash: reservation.requestHash });
      return true;
    },
    releaseReservation: async (reservation) => {
      const key = mealKey(reservation.userId, reservation.mealId);
      if (reservations.get(key)?.reservationId === reservation.reservationId) reservations.delete(key);
    },
    markRegistered: async (record, googleHealthName) => {
      meals.set(mealKey(record.userId, record.mealId), {
        ...meals.get(mealKey(record.userId, record.mealId)),
        record,
        status: 'registered',
        googleHealthName,
      });
    },
  };
  let analysisIndex = 0;
  let healthIndex = 0;
  return {
    calls,
    meal: () => meals.get(mealKey('user1', 'text-1')),
    dependencies: {
      analysisClient: {
        responses: {
          parse: async (request) => {
            calls.analysis.push(request);
            return { output_parsed: analysisOutputs[analysisIndex++] };
          },
        },
      },
      authRepository: {
        findSubByToken: async (token) => {
          calls.tokenLookups.push(token);
          return token === 'token-1' ? 'user1' : null;
        },
        getRefreshToken: async (sub) => sub === 'user1' ? 'refresh-token' : null,
      },
      mealRepository,
      clientId: 'client-id',
      clientSecret: 'client-secret',
      healthClient: {
        getDataPoint: async () => { throw { code: 5 }; },
        createDataPoint: async (request) => {
          calls.googleHealth.push(request);
          const outcome = healthOutcomes[healthIndex++];
          if (outcome instanceof Error) throw outcome;
          return [{ promise: async () => [{ name: request.dataPoint.name }] }];
        },
      },
    },
  };
}

test('an unauthenticated text request is not sent to OpenAI', async () => {
  const request = requestFixture();
  request.headers.authorization = 'Bearer invalid';
  const response = responseFixture();
  const fixture = endpointFixture();

  await handleTextMealRequest({ request, response, ...fixture.dependencies });

  assert.equal(response.statusCode, 401);
  assert.equal(fixture.calls.analysis.length, 0);
  assert.equal(fixture.calls.googleHealth.length, 0);
});

test('an invalid input time is rejected before text analysis', async () => {
  const response = responseFixture();
  const fixture = endpointFixture();

  await handleTextMealRequest({
    request: requestFixture({ inputAt: 'not-a-time' }),
    response,
    ...fixture.dependencies,
  });

  assert.equal(response.statusCode, 400);
  assert.equal(fixture.calls.analysis.length, 0);
});

test('natural text without a date uses input time and reaches the authenticated Google Health record', async () => {
  const response = responseFixture();
  const fixture = endpointFixture({
    analysisOutputs: [textAnalysis({
      foodDisplayName: 'トーストとヨーグルト',
      eatenAt: null,
    })],
  });

  await handleTextMealRequest({ request: requestFixture(), response, ...fixture.dependencies });

  assert.equal(response.statusCode, 201);
  assert.equal(fixture.calls.analysis.length, 1);
  assert.equal(fixture.calls.analysis[0].model, 'gpt-5.6-luna');
  assert.deepEqual(fixture.calls.tokenLookups, ['token-1']);
  assert.equal(response.body.record.userId, 'user1');
  assert.equal(response.body.record.eatenAt, '2026-09-20T23:00:00.000Z');
  assert.deepEqual(response.body.record.nutrition.energyKcal.estimated,
    { value: 420, origin: 'textAnalysis' });
  assert.deepEqual(response.body.record.nutrition.proteinGrams.estimated,
    { value: 12, origin: 'textAnalysis' });
  assert.equal(fixture.calls.googleHealth.length, 1);
});

test('relative date text is converted from the input time', async () => {
  const response = responseFixture();
  const fixture = endpointFixture({
    analysisOutputs: [textAnalysis({
      foodDisplayName: 'カレー',
      eatenAt: '2026-09-20T20:00:00+09:00',
    })],
  });

  await handleTextMealRequest({
    request: requestFixture({ text: '昨日の夜 カレー' }),
    response,
    ...fixture.dependencies,
  });

  assert.equal(response.statusCode, 201);
  assert.equal(response.body.record.eatenAt, '2026-09-20T11:00:00.000Z');
});

test('explicit nutrition remains confirmed and omitted values remain estimates', async () => {
  const response = responseFixture();
  const fixture = endpointFixture({
    analysisOutputs: [textAnalysis({
      foodDisplayName: 'カップ麺',
      eatenAt: null,
      confirmedEnergyKcal: 351,
      estimatedEnergyKcal: 999,
      proteinGrams: 9,
      carbohydrateGrams: 55,
      fatGrams: 14,
    })],
  });

  await handleTextMealRequest({
    request: requestFixture({ text: 'カップ麺 351kcal' }),
    response,
    ...fixture.dependencies,
  });

  assert.deepEqual(response.body.record.nutrition.energyKcal, {
    estimated: { value: 999, origin: 'textAnalysis' },
    confirmed: { value: 351, origin: 'userInput' },
  });
  assert.deepEqual(response.body.record.nutrition.proteinGrams.estimated,
    { value: 9, origin: 'textAnalysis' });
  assert.deepEqual(fixture.calls.googleHealth[0].dataPoint.nutritionLog.energy, { kcal: 351 });
});

test('a registered text retry reuses the first analysis', async () => {
  const fixture = endpointFixture({
    analysisOutputs: [textAnalysis({ foodDisplayName: '初回の食事' }), textAnalysis({ foodDisplayName: '別の出力' })],
  });
  const firstResponse = responseFixture();
  const retryResponse = responseFixture();

  await handleTextMealRequest({ request: requestFixture(), response: firstResponse,
    ...fixture.dependencies });
  await handleTextMealRequest({ request: requestFixture(), response: retryResponse,
    ...fixture.dependencies });

  assert.equal(firstResponse.statusCode, 201);
  assert.equal(retryResponse.statusCode, 200);
  assert.equal(fixture.calls.analysis.length, 1);
  assert.equal(fixture.meal().record.foodDisplayName, '初回の食事');
  assert.equal(fixture.calls.googleHealth.length, 1);
});

test('a saved meal ID rejects a retry with different text before analysis', async () => {
  const fixture = endpointFixture();
  const firstResponse = responseFixture();
  const changedResponse = responseFixture();

  await handleTextMealRequest({ request: requestFixture(), response: firstResponse, ...fixture.dependencies });
  await handleTextMealRequest({
    request: requestFixture({ text: '別の食事' }),
    response: changedResponse,
    ...fixture.dependencies,
  });

  assert.equal(firstResponse.statusCode, 201);
  assert.equal(changedResponse.statusCode, 409);
  assert.equal(fixture.calls.analysis.length, 1);
});
