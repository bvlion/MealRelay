'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const { MealRecordError } = require('../src/mealRecord');
const { handleImageMealRequest } = require('../src/imageMealEndpoint');

function multipartRequest() {
  const boundary = 'meal-relay-boundary';
  const body = Buffer.from(
    `--${boundary}\r\nContent-Disposition: form-data; name="mealId"\r\n\r\nphoto-1\r\n` +
    `--${boundary}\r\nContent-Disposition: form-data; name="capturedAt"\r\n\r\n2026-09-21T12:30:00+09:00\r\n` +
    `--${boundary}\r\nContent-Disposition: form-data; name="image"; filename="meal.jpg"\r\n` +
    'Content-Type: image/jpeg\r\n\r\nimage\r\n' +
    `--${boundary}--\r\n`,
  );
  const headers = {
    authorization: 'Bearer token-1',
    'content-type': `multipart/form-data; boundary=${boundary}`,
  };
  return {
    method: 'POST',
    headers,
    rawBody: body,
    get: (name) => headers[name.toLowerCase()],
    is: (mediaType) => mediaType === 'multipart/form-data',
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

function mealAnalysis(foodDisplayName = 'ご飯 約150g、焼き鮭 約80g') {
  return {
    foodDisplayName,
    estimated: {
      energyKcal: 450,
      proteinGrams: 28,
      carbohydrateGrams: 62,
      fatGrams: 10,
    },
    confirmed: {
      energyKcal: { value: 430, origin: 'packageLabel' },
      proteinGrams: null,
      carbohydrateGrams: null,
      fatGrams: null,
    },
  };
}

function endpointFixture({ analysisOutputs = [mealAnalysis()], healthOutcomes = ['success'] } = {}) {
  const calls = { analysis: [], googleHealth: [], tokenLookups: [] };
  const meals = new Map();
  const mealKey = (userId, mealId) => `${userId}:${mealId}`;
  const mealRepository = {
    find: async (userId, mealId) => meals.get(mealKey(userId, mealId)) ?? null,
    saveIfAbsent: async (record) => {
      const key = mealKey(record.userId, record.mealId);
      const savedMeal = meals.get(key);
      if (savedMeal) {
        if (JSON.stringify(savedMeal.record) !== JSON.stringify(record)) {
          throw new MealRecordError('Meal ID already has different content', 409);
        }
        return savedMeal.status;
      }
      meals.set(key, { record, status: 'pending' });
      return 'new';
    },
    markRegistered: async (record, googleHealthName) => {
      meals.set(mealKey(record.userId, record.mealId), {
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
    meal: () => meals.get(mealKey('user1', 'photo-1')),
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

test('an unauthenticated image is not sent to OpenAI', async () => {
  const request = multipartRequest();
  request.headers.authorization = 'Bearer invalid';
  const response = responseFixture();
  const fixture = endpointFixture();

  await handleImageMealRequest({ request, response, ...fixture.dependencies });

  assert.equal(response.statusCode, 401);
  assert.equal(fixture.calls.analysis.length, 0);
  assert.equal(fixture.calls.googleHealth.length, 0);
});

test('an initial image is analyzed by GPT-5.6 Luna and registered through the shared meal flow', async () => {
  const response = responseFixture();
  const fixture = endpointFixture();

  await handleImageMealRequest({ request: multipartRequest(), response, ...fixture.dependencies });

  assert.equal(response.statusCode, 201);
  assert.equal(fixture.calls.analysis.length, 1);
  assert.equal(fixture.calls.analysis[0].model, 'gpt-5.6-luna');
  assert.deepEqual(fixture.calls.tokenLookups, ['token-1']);
  assert.equal(response.body.record.eatenAt, '2026-09-21T03:30:00.000Z');
  assert.deepEqual(response.body.record.nutrition.energyKcal, {
    estimated: { value: 450, origin: 'imageAnalysis' },
    confirmed: { value: 430, origin: 'packageLabel' },
  });
  assert.deepEqual(fixture.calls.googleHealth[0].dataPoint.nutritionLog.energy, { kcal: 430 });
});

test('a pending retry reuses the first analysis even when a later model output would differ', async () => {
  const fixture = endpointFixture({
    analysisOutputs: [mealAnalysis('初回の食事 約1人前'), mealAnalysis('再解析なら異なる食事')],
    healthOutcomes: [new Error('Google Health response lost'), 'success'],
  });
  const firstResponse = responseFixture();
  const retryResponse = responseFixture();

  await handleImageMealRequest({ request: multipartRequest(), response: firstResponse,
    ...fixture.dependencies });
  await handleImageMealRequest({ request: multipartRequest(), response: retryResponse,
    ...fixture.dependencies });

  assert.equal(firstResponse.statusCode, 500);
  assert.equal(retryResponse.statusCode, 201);
  assert.equal(fixture.calls.analysis.length, 1);
  assert.equal(fixture.meal().record.foodDisplayName, '初回の食事 約1人前');
  assert.equal(fixture.calls.googleHealth.length, 2);
});

test('a registered retry does not invoke OpenAI again', async () => {
  const fixture = endpointFixture({ analysisOutputs: [mealAnalysis(), mealAnalysis('別の出力')] });
  const firstResponse = responseFixture();
  const retryResponse = responseFixture();

  await handleImageMealRequest({ request: multipartRequest(), response: firstResponse,
    ...fixture.dependencies });
  await handleImageMealRequest({ request: multipartRequest(), response: retryResponse,
    ...fixture.dependencies });

  assert.equal(firstResponse.statusCode, 201);
  assert.equal(retryResponse.statusCode, 200);
  assert.equal(fixture.calls.analysis.length, 1);
  assert.equal(fixture.calls.googleHealth.length, 1);
});
