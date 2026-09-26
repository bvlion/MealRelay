'use strict';

const { createHash } = require('node:crypto');
const { v4: googleHealth } = require('@google-cloud/health');

const NUTRITION_PARENT = 'users/me/dataTypes/nutrition-log';
const MILLISECONDS_PER_SECOND = 1000;
const NANOSECONDS_PER_MILLISECOND = 1000000;
// A one-second interval represents the known meal instant without inventing a meal duration.
const MEAL_TIME_WINDOW_MILLISECONDS = 1000;

function mealDocumentId(record) {
  return createHash('sha256').update(record.userId).update('\0').update(record.mealId).digest('hex');
}

function toGoogleHealthDataPoint(record) {
  const nutrition = record.nutrition;
  const selected = (field) => nutrition[field]?.confirmed ?? nutrition[field]?.estimated;
  const startTime = new Date(record.eatenAt);
  const nutritionLog = {
    interval: {
      startTime: startTime.toISOString(),
      startUtcOffset: record.utcOffset,
      endTime: new Date(startTime.getTime() + MEAL_TIME_WINDOW_MILLISECONDS).toISOString(),
      endUtcOffset: record.utcOffset,
    },
    foodDisplayName: record.foodDisplayName,
  };
  if (record.mealType) nutritionLog.mealType = record.mealType;
  if (selected('energyKcal')) nutritionLog.energy = { kcal: selected('energyKcal').value };
  if (selected('carbohydrateGrams')) {
    nutritionLog.totalCarbohydrate = { grams: selected('carbohydrateGrams').value };
  }
  if (selected('fatGrams')) nutritionLog.totalFat = { grams: selected('fatGrams').value };
  if (selected('proteinGrams')) {
    nutritionLog.nutrients = [{ nutrient: 'PROTEIN', quantity: { grams: selected('proteinGrams').value } }];
  }
  return {
    dataSource: { recordingMethod: 'DERIVED' },
    nutritionLog,
  };
}

function toGoogleHealthClientDataPoint(dataPoint) {
  const toTimestamp = (value) => {
    const milliseconds = Date.parse(value);
    const seconds = Math.floor(milliseconds / MILLISECONDS_PER_SECOND);
    return {
      seconds,
      nanos: (milliseconds - seconds * MILLISECONDS_PER_SECOND) * NANOSECONDS_PER_MILLISECOND,
    };
  };
  const toDuration = (value) => ({ seconds: Number(value.slice(0, -1)) });
  const interval = dataPoint.nutritionLog.interval;
  return {
    ...dataPoint,
    nutritionLog: {
      ...dataPoint.nutritionLog,
      interval: {
        startTime: toTimestamp(interval.startTime),
        startUtcOffset: toDuration(interval.startUtcOffset),
        endTime: toTimestamp(interval.endTime),
        endUtcOffset: toDuration(interval.endUtcOffset),
      },
    },
  };
}

class GoogleHealthPendingError extends Error {
  constructor() {
    super('Google Health registration result is pending');
    this.status = 503;
  }
}

async function createNutritionLog({ oauthClient, dataPoint, isRetry = false,
  healthClient: providedHealthClient }) {
  // Google Health currently ignores client-assigned IDs on writes. Repeating a POST after an
  // uncertain result can create a duplicate server-generated resource, so pending writes are not
  // submitted again.
  if (isRetry) throw new GoogleHealthPendingError();

  const healthClient = providedHealthClient ?? new googleHealth.DataPointsServiceClient({
    authClient: oauthClient,
    fallback: true,
  });
  try {
    const [operation] = await healthClient.createDataPoint({
      parent: NUTRITION_PARENT,
      dataPoint: toGoogleHealthClientDataPoint(dataPoint),
    });
    const [createdPoint] = await operation.promise();
    if (typeof createdPoint?.name !== 'string' || !createdPoint.name) {
      throw new Error('Google Health did not return the created nutrition log name');
    }
    return createdPoint.name;
  } finally {
    if (!providedHealthClient) await healthClient.close();
  }
}

module.exports = { GoogleHealthPendingError, mealDocumentId, toGoogleHealthDataPoint, createNutritionLog };
