'use strict';

const assert = require('node:assert/strict');
const test = require('node:test');
const { MealRecordError, normalizeImageMeal, normalizeTextMeal } = require('../src/mealRecord');
const { toGoogleHealthDataPoint, createNutritionLog } = require('../src/googleHealthNutrition');

test('image and text results share a model with per-field provenance', () => {
  const analysis = {
    foodDisplayName: 'Rice and vegetables',
    estimated: { energyKcal: 430, proteinGrams: 12, carbohydrateGrams: 66, fatGrams: 9 },
    confirmed: { energyKcal: { value: 400, origin: 'packageLabel' },
      proteinGrams: { value: 0, origin: 'userInput' } },
  };
  const image = normalizeImageMeal({
    userId: 'user1', mealId: 'photo-1', capturedAt: '2026-01-02T19:30:00+09:00', analysis,
  });
  const text = normalizeTextMeal({
    userId: 'user1', mealId: 'text-1', inputAt: '2026-01-03T20:00:00+09:00',
    analysis: { ...analysis, eatenAt: '2026-01-01T19:30:00+09:00' },
  });

  assert.equal(image.eatenAt, '2026-01-02T10:30:00.000Z');
  assert.equal(image.utcOffset, '32400s');
  assert.equal(image.nutrition.energyKcal.estimated.origin, 'imageAnalysis');
  assert.equal(text.eatenAt, '2026-01-01T10:30:00.000Z');
  assert.equal(text.nutrition.energyKcal.estimated.origin, 'textAnalysis');
  assert.deepEqual(image.nutrition.energyKcal.confirmed, { value: 400, origin: 'packageLabel' });

  const dataPoint = toGoogleHealthDataPoint(image);
  assert.deepEqual(dataPoint.nutritionLog.interval, {
    startTime: image.eatenAt, startUtcOffset: '32400s',
    endTime: '2026-01-02T10:30:01.000Z', endUtcOffset: '32400s',
  });
  assert.deepEqual(dataPoint.nutritionLog.energy, { kcal: 400 });
  assert.deepEqual(dataPoint.nutritionLog.totalCarbohydrate, { grams: 66 });
  assert.deepEqual(dataPoint.nutritionLog.totalFat, { grams: 9 });
  assert.deepEqual(dataPoint.nutritionLog.nutrients,
    [{ nutrient: 'PROTEIN', quantity: { grams: 0 } }]);
  assert.equal(dataPoint.name, undefined);
});

test('text without an interpreted time uses the input time', () => {
  const record = normalizeTextMeal({
    userId: 'user1', mealId: 'text-2', inputAt: '2026-03-01T08:00:00Z',
    analysis: { foodDisplayName: 'Toast' },
  });
  assert.equal(record.eatenAt, '2026-03-01T08:00:00.000Z');
});

test('meal time retains a negative UTC offset separately from its UTC instant', () => {
  const record = normalizeTextMeal({
    userId: 'user1', mealId: 'text-4', inputAt: '2026-03-01T08:00:00-04:30',
    analysis: { foodDisplayName: 'Toast' },
  });
  assert.equal(record.eatenAt, '2026-03-01T12:30:00.000Z');
  assert.equal(record.utcOffset, '-16200s');
});

test('meal IDs are not limited by an arbitrary input length', () => {
  const mealId = 'meal-'.repeat(50);
  const record = normalizeImageMeal({
    userId: 'user1', mealId, capturedAt: '2026-03-01T08:00:00Z',
    analysis: { foodDisplayName: 'Toast' },
  });
  assert.equal(record.mealId, mealId);
  assert.equal(toGoogleHealthDataPoint(record).name, undefined);
});

test('invalid times and confirmed values are rejected', () => {
  assert.throws(() => normalizeImageMeal({
    userId: 'user1', mealId: 'photo-2', capturedAt: '2026-01-02T19:30:00',
    analysis: { foodDisplayName: 'Rice' },
  }), MealRecordError);
  assert.throws(() => normalizeTextMeal({
    userId: 'user1', mealId: 'text-3', inputAt: '2026-01-02T19:30:00Z',
    analysis: { foodDisplayName: 'Rice', confirmed: { energyKcal: { value: -1, origin: 'userInput' } } },
  }), MealRecordError);
  assert.throws(() => normalizeImageMeal({
    userId: 'user1', mealId: 'photo-4', capturedAt: '2026-02-30T19:30:00Z',
    analysis: { foodDisplayName: 'Rice' },
  }), MealRecordError);
  assert.throws(() => normalizeImageMeal({
    userId: 'user1', mealId: 'photo-5', capturedAt: '2026-W01-4T19:30:00+09:00',
    analysis: { foodDisplayName: 'Rice' },
  }), MealRecordError);
});

test('Google Health confirms an immediately completed registration', async () => {
  const dataPoint = toGoogleHealthDataPoint(normalizeImageMeal({
    userId: 'user1', mealId: 'photo-3', capturedAt: '2026-01-02T19:30:00Z',
    analysis: { foodDisplayName: 'Rice' },
  }));
  let sent;
  const healthClient = {
    createDataPoint: async (request) => {
      sent = request;
      return [{
        done: true,
        promise: async () => [{
          name: 'users/health-user/dataTypes/nutrition-log/dataPoints/server-generated-1',
        }],
      }];
    },
  };
  const name = await createNutritionLog({ healthClient, dataPoint });
  assert.equal(name,
    'users/health-user/dataTypes/nutrition-log/dataPoints/server-generated-1');
  assert.equal(sent.parent, 'users/me/dataTypes/nutrition-log');
  assert.equal(sent.dataPoint.name, undefined);
  assert.deepEqual(sent.dataPoint.nutritionLog.interval.startUtcOffset, { seconds: 0 });
});

test('official Google Health client uses the selected user OAuth client', async () => {
  const dataPoint = toGoogleHealthDataPoint(normalizeImageMeal({
    userId: 'user1', mealId: 'photo-6', capturedAt: '2026-01-02T19:30:00Z',
    analysis: { foodDisplayName: 'Rice' },
  }));
  let request;
  const oauthClient = {
    universeDomain: 'googleapis.com',
    getRequestHeaders: async () => ({}),
    fetch: async (url, options) => {
      request = { url, options };
      return new Response(JSON.stringify({
        name: 'operations/create-nutrition',
        done: true,
        response: {
          '@type': 'type.googleapis.com/google.devicesandservices.health.v4.DataPoint',
          name: 'users/health-user/dataTypes/nutrition-log/dataPoints/server-generated-2',
        },
      }), { status: 200, headers: { 'content-type': 'application/json' } });
    },
  };

  await createNutritionLog({ oauthClient, dataPoint });

  assert.equal(request.options.method, 'POST');
  assert.match(request.url, /^https:\/\/health\.googleapis\.com(?::443)?\/v4\/users\/me\/dataTypes\/nutrition-log\/dataPoints\?/);
  assert.equal(JSON.parse(request.options.body).name, undefined);
});
