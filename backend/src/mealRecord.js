'use strict';

const { DateTime } = require('luxon');
const isRFC3339 = require('validator/lib/isRFC3339');

const NUTRITION_FIELDS = ['energyKcal', 'proteinGrams', 'carbohydrateGrams', 'fatGrams'];
const MEAL_TYPES = new Set(['BREAKFAST', 'LUNCH', 'DINNER', 'SNACK']);
const CONFIRMED_ORIGINS = new Set(['packageLabel', 'userInput']);

class MealRecordError extends Error {
  constructor(message, status = 400) {
    super(message);
    this.status = status;
  }
}

function validateMealInput({ route, userId, mealId, analysis }) {
  if (route !== 'image' && route !== 'text') {
    throw new MealRecordError('Meal route is invalid');
  }
  if (typeof userId !== 'string' || !userId ||
      typeof mealId !== 'string' || !mealId) {
    throw new MealRecordError('Meal identity is invalid');
  }
  if (!analysis || typeof analysis.foodDisplayName !== 'string' ||
      !analysis.foodDisplayName.trim()) {
    throw new MealRecordError('Meal content is required');
  }
  if (analysis.mealType !== undefined && !MEAL_TYPES.has(analysis.mealType)) {
    throw new MealRecordError('Meal type is invalid');
  }
}

function normalizeMealTime(eatenAt) {
  const mealTime = typeof eatenAt === 'string' ? DateTime.fromISO(eatenAt, { setZone: true }) : null;
  if (typeof eatenAt !== 'string' || !isRFC3339(eatenAt) || !mealTime.isValid) {
    throw new MealRecordError('Meal time must include a UTC offset');
  }
  return { eatenAt: mealTime.toUTC().toISO(), utcOffset: `${mealTime.offset * 60}s` };
}

function normalizeNutrition(route, analysis) {
  const nutrition = {};
  for (const field of NUTRITION_FIELDS) {
    const estimated = analysis.estimated?.[field];
    const confirmed = analysis.confirmed?.[field];
    if (estimated === undefined && confirmed === undefined) continue;
    const value = {};
    if (estimated !== undefined) {
      if (typeof estimated !== 'number' || !Number.isFinite(estimated) || estimated < 0) {
        throw new MealRecordError(`${field} estimate is invalid`);
      }
      value.estimated = { value: estimated, origin: route === 'image' ? 'imageAnalysis' : 'textAnalysis' };
    }
    if (confirmed !== undefined) {
      if (!confirmed || typeof confirmed.value !== 'number' ||
          !Number.isFinite(confirmed.value) || confirmed.value < 0 ||
          !CONFIRMED_ORIGINS.has(confirmed.origin)) {
        throw new MealRecordError(`${field} confirmed value is invalid`);
      }
      value.confirmed = { value: confirmed.value, origin: confirmed.origin };
    }
    nutrition[field] = value;
  }
  return nutrition;
}

function normalizeMeal({ route, userId, mealId, eatenAt, analysis }) {
  validateMealInput({ route, userId, mealId, analysis });
  return {
    userId,
    mealId,
    route,
    ...normalizeMealTime(eatenAt),
    foodDisplayName: analysis.foodDisplayName.trim(),
    ...(analysis.mealType && { mealType: analysis.mealType }),
    nutrition: normalizeNutrition(route, analysis),
  };
}

function normalizeImageMeal({ userId, mealId, capturedAt, analysis }) {
  return normalizeMeal({ route: 'image', userId, mealId, eatenAt: capturedAt, analysis });
}

function normalizeTextMeal({ userId, mealId, inputAt, analysis }) {
  return normalizeMeal({ route: 'text', userId, mealId, eatenAt: analysis?.eatenAt ?? inputAt, analysis });
}

function validateImageMealRetry({ record, userId, mealId, capturedAt }) {
  const capturedTime = normalizeMealTime(capturedAt);
  if (record.route !== 'image' || record.userId !== userId || record.mealId !== mealId ||
      record.eatenAt !== capturedTime.eatenAt || record.utcOffset !== capturedTime.utcOffset) {
    throw new MealRecordError('Meal ID already has different content', 409);
  }
}

module.exports = {
  MealRecordError,
  normalizeImageMeal,
  normalizeTextMeal,
  validateImageMealRetry,
};
