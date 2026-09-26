'use strict';

const { createHash } = require('node:crypto');
const { AuthenticationError } = require('./errors');
const { logBackendError } = require('./errorLogging');
const { authenticateMealRelayRequest } = require('./apiAuthentication');
const { GoogleHealthPendingError } = require('./googleHealthNutrition');
const { MealRecordError, normalizeMealTime, validateTextMealRetry } = require('./mealRecord');
const { completeMealRegistration, registerMeal } = require('./registerMeal');
const { analyzeTextMeal } = require('./textMealAnalysis');
const { TextMealError } = require('./textMealError');
const { parseTextMealRequest } = require('./textMealRequest');

async function registerTextMeal({ authorization, userId, text, inputAt, mealId,
  analysisClient, authRepository, mealRepository, clientId, clientSecret, healthClient }) {
  normalizeMealTime(inputAt);
  const requestHash = createHash('sha256').update(JSON.stringify({ text, inputAt })).digest('hex');
  const reservation = await mealRepository.reserve(userId, mealId, requestHash);
  if (reservation.type === 'saved') {
    validateTextMealRetry({ record: reservation.savedMeal.record, userId, mealId });
    return completeMealRegistration({
      ...reservation.savedMeal,
      authRepository,
      clientId,
      clientSecret,
      healthClient,
      mealRepository,
    });
  }
  if (reservation.type === 'different') throw new MealRecordError('Meal ID already has different content', 409);
  if (reservation.type === 'active') throw new MealRecordError('Meal is already being analyzed', 503);

  let analysis;
  try {
    analysis = await analyzeTextMeal({ client: analysisClient, text, inputAt });
  } catch (error) {
    await mealRepository.releaseReservation(reservation);
    throw error;
  }
  return registerMeal({
    route: 'text',
    authorization,
    authenticatedUserId: userId,
    mealId,
    occurredAt: inputAt,
    analysis,
    authRepository,
    mealRepository,
    clientId,
    clientSecret,
    healthClient,
    reservation,
  });
}

async function handleTextMealRequest({ request, response, analysisClient, authRepository,
  mealRepository, clientId, clientSecret, healthClient }) {
  response.set('Cache-Control', 'no-store');
  if (request.method !== 'POST') {
    response.status(405).end();
    return;
  }
  if (!request.is('application/json')) {
    response.status(415).json({ error: 'JSON is required' });
    return;
  }

  try {
    const authorization = request.get('authorization');
    const userId = await authenticateMealRelayRequest({ authorization, repository: authRepository });
    const { text, inputAt, mealId } = parseTextMealRequest(request);
    const result = await registerTextMeal({
      authorization,
      userId,
      text,
      inputAt,
      mealId,
      analysisClient,
      authRepository,
      mealRepository,
      clientId,
      clientSecret,
      healthClient,
    });
    response.status(result.isAlreadyRegistered ? 200 : 201).json(result);
  } catch (error) {
    const isHandled = error instanceof AuthenticationError || error instanceof GoogleHealthPendingError ||
      error instanceof TextMealError || error instanceof MealRecordError;
    const responseStatus = isHandled ? error.status : 500;
    logBackendError({
      message: 'Text meal request failed',
      error,
      responseStatus,
      reason: isHandled ? error.message : undefined,
      severity: responseStatus >= 500 ? 'ERROR' : 'WARNING',
    });
    if (isHandled) {
      response.status(responseStatus).json({ error: error.message });
      return;
    }
    response.status(500).json({ error: 'Meal could not be registered' });
  }
}

module.exports = { handleTextMealRequest };
