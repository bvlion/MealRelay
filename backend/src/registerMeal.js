'use strict';

const { authenticateMealRelayRequest } = require('./apiAuthentication');
const { createGoogleOAuth } = require('./googleOAuth');
const { getGoogleHealthOAuthClient } = require('./healthCredentials');
const { normalizeImageMeal, normalizeTextMeal } = require('./mealRecord');
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
  healthClient, requestHash }) {
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
  const status = requestHash
    ? await mealRepository.saveReserved(record, requestHash)
    : await mealRepository.saveIfAbsent(record);
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

module.exports = { completeMealRegistration, registerMeal };
