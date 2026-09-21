'use strict';

const { createHash } = require('node:crypto');
const { v4: googleHealth } = require('@google-cloud/health');

const NUTRITION_PARENT = 'users/me/dataTypes/nutrition-log';
const GOOGLE_RPC_NOT_FOUND = 5;
const GOOGLE_RPC_ALREADY_EXISTS = 6;
const MILLISECONDS_PER_SECOND = 1000;
const NANOSECONDS_PER_MILLISECOND = 1000000;
// A one-second interval represents the known meal instant without inventing a meal duration.
const MEAL_TIME_WINDOW_MILLISECONDS = 1000;
// Google Health accepts a client-provided data point ID of at most 63 characters.
const GOOGLE_HEALTH_MAX_DATA_POINT_ID_LENGTH = 63;

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
    name: `users/me/dataTypes/nutrition-log/dataPoints/${mealDocumentId(record).slice(0, GOOGLE_HEALTH_MAX_DATA_POINT_ID_LENGTH)}`,
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
    super('Google Health has not confirmed nutrition log creation');
    this.status = 503;
  }
}

async function getNutritionLog({ healthClient, dataPoint }) {
  const dataPointId = dataPoint.name.split('/').at(-1);
  try {
    const [data] = await healthClient.getDataPoint({ name: dataPoint.name });
    if (data?.name?.split('/').at(-1) !== dataPointId) {
      throw new Error('Google Health returned a different nutrition log');
    }
    return data.name;
  } catch (error) {
    if (error.code === GOOGLE_RPC_NOT_FOUND) return null;
    throw error;
  }
}

async function createNutritionLog({ oauthClient, dataPoint, isRetry = false,
  healthClient: providedHealthClient }) {
  const healthClient = providedHealthClient ?? new googleHealth.DataPointsServiceClient({
    authClient: oauthClient,
    fallback: true,
  });
  try {
    if (isRetry) {
      const existingName = await getNutritionLog({ healthClient, dataPoint });
      if (existingName) return existingName;
    }

    let operation;
    try {
      [operation] = await healthClient.createDataPoint({
        parent: NUTRITION_PARENT,
        dataPoint: toGoogleHealthClientDataPoint(dataPoint),
      });
    } catch (error) {
      if (error.code !== GOOGLE_RPC_ALREADY_EXISTS) throw error;
      const existingName = await getNutritionLog({ healthClient, dataPoint });
      if (existingName) return existingName;
      throw new GoogleHealthPendingError();
    }
    const [createdPoint] = await operation.promise();
    if (createdPoint?.name?.split('/').at(-1) !== dataPoint.name.split('/').at(-1)) {
      throw new Error('Google Health did not confirm nutrition log creation');
    }
    return createdPoint.name;
  } finally {
    if (!providedHealthClient) await healthClient.close();
  }
}

module.exports = { GoogleHealthPendingError, mealDocumentId, toGoogleHealthDataPoint, createNutritionLog };
