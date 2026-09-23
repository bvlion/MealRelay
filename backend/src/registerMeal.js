'use strict';

const { authenticateMealRelayRequest } = require('./apiAuthentication');
const { createGoogleOAuth } = require('./googleOAuth');
const { getGoogleHealthOAuthClient } = require('./healthCredentials');
const { MealRecordError, normalizeImageMeal, normalizeTextMeal } = require('./mealRecord');
const { toGoogleHealthDataPoint, createNutritionLog } = require('./googleHealthNutrition');

async function completeMealRegistration({ record, status, authRepository, clientId, clientSecret,
  createOAuthClient = createGoogleOAuth, healthClient, mealRepository,
  mealNotifier = { notifyRegistration: async () => {} } }) {
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
  if (record.route === 'image') {
    try {
      await mealNotifier.notifyRegistration(record);
    } catch {
      // Notification failure must not change the completed Google Health registration.
    }
  }
  return { record, isAlreadyRegistered: false };
}

async function registerMeal({ route, authorization, authenticatedUserId, mealId, occurredAt, analysis,
  authRepository, mealRepository, clientId, clientSecret, createOAuthClient = createGoogleOAuth,
  healthClient, reservation, mealNotifier }) {
  if (route !== 'image' && route !== 'text') {
    throw new Error('Meal route is invalid');
  }
  const userId = authenticatedUserId ?? await authenticateMealRelayRequest({
    authorization,
    repository: authRepository,
  });
  if (!reservation) throw new Error('Meal reservation is required');
  let isSaved = false;
  try {
    const record = route === 'image'
      ? normalizeImageMeal({ userId, mealId, capturedAt: occurredAt, analysis })
      : normalizeTextMeal({ userId, mealId, inputAt: occurredAt, analysis });
    const status = await saveReservedMeal(mealRepository, record, reservation);
    isSaved = true;
    return completeMealRegistration({
      record,
      status,
      authRepository,
      clientId,
      clientSecret,
      createOAuthClient,
      healthClient,
      mealRepository,
      mealNotifier,
    });
  } catch (error) {
    if (!isSaved) await mealRepository.releaseReservation(reservation);
    throw error;
  }
}

async function saveReservedMeal(mealRepository, record, reservation) {
  if (await mealRepository.saveReserved(record, reservation)) return 'new';
  throw new MealRecordError('Meal is already being analyzed', 503);
}

module.exports = { completeMealRegistration, registerMeal };
