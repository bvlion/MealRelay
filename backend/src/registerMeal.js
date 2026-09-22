'use strict';

const { authenticateMealRelayRequest } = require('./apiAuthentication');
const { createGoogleOAuth } = require('./googleOAuth');
const { getGoogleHealthOAuthClient } = require('./healthCredentials');
const { MealRecordError, normalizeImageMeal, normalizeTextMeal } = require('./mealRecord');
const { toGoogleHealthDataPoint, createNutritionLog } = require('./googleHealthNutrition');

async function completeMealRegistration({ record, status, authRepository, clientId, clientSecret,
  createOAuthClient = createGoogleOAuth, healthClient, mealRepository }) {
  if (status === 'registered') return { record, isAlreadyRegistered: true };

  const oauthClient = await getGoogleHealthOAuthClient({
    sub: record.userId,
    repository: authRepository,
    createOAuthClient: () => createOAuthClient(clientId, clientSecret),
  });
  const googleHealthName = await createNutritionLog({
    oauthClient,
    healthClient,
    dataPoint: toGoogleHealthDataPoint(record),
    isRetry: status !== 'new',
  });
  await mealRepository.markRegistered(record, googleHealthName);
  return { record, isAlreadyRegistered: false };
}

async function registerMeal({ route, authorization, authenticatedUserId, mealId, occurredAt, analysis,
  authRepository, mealRepository, clientId, clientSecret, createOAuthClient = createGoogleOAuth,
  healthClient, reservation }) {
  if (route !== 'image' && route !== 'text') {
    throw new Error('Meal route is invalid');
  }
  const userId = authenticatedUserId ?? await authenticateMealRelayRequest({
    authorization,
    repository: authRepository,
  });
  const record = route === 'image'
    ? normalizeImageMeal({ userId, mealId, capturedAt: occurredAt, analysis })
    : normalizeTextMeal({ userId, mealId, inputAt: occurredAt, analysis });
  if (!reservation) throw new Error('Meal reservation is required');
  const status = await saveReservedMeal(mealRepository, record, reservation);
  return completeMealRegistration({
    record,
    status,
    authRepository,
    clientId,
    clientSecret,
    createOAuthClient,
    healthClient,
    mealRepository,
  });
}

async function saveReservedMeal(mealRepository, record, reservation) {
  if (await mealRepository.saveReserved(record, reservation)) return 'new';
  throw new MealRecordError('Meal is already being analyzed', 503);
}

module.exports = { completeMealRegistration, registerMeal };
