'use strict';

const { authenticateMealRelayRequest } = require('./apiAuthentication');
const { createGoogleOAuth } = require('./googleOAuth');
const { getGoogleHealthOAuthClient } = require('./healthCredentials');
const { normalizeImageMeal, normalizeTextMeal } = require('./mealRecord');
const { toGoogleHealthDataPoint, createNutritionLog } = require('./googleHealthNutrition');

async function registerMeal({ route, authorization, mealId, occurredAt, analysis,
  authRepository, mealRepository, clientId, clientSecret, createOAuthClient = createGoogleOAuth,
  healthClient }) {
  if (route !== 'image' && route !== 'text') {
    throw new Error('Meal route is invalid');
  }
  const userId = await authenticateMealRelayRequest({ authorization, repository: authRepository });
  const record = route === 'image'
    ? normalizeImageMeal({ userId, mealId, capturedAt: occurredAt, analysis })
    : normalizeTextMeal({ userId, mealId, inputAt: occurredAt, analysis });
  const status = await mealRepository.saveIfAbsent(record);
  if (status === 'registered') return { record, isAlreadyRegistered: true };

  const oauthClient = await getGoogleHealthOAuthClient({
    sub: userId,
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

module.exports = { registerMeal };
