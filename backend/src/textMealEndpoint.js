'use strict';

const { createHash } = require('node:crypto');
const { AuthenticationError } = require('./errors');
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
  const acquisition = mealRepository.acquire
    ? await mealRepository.acquire(userId, mealId, requestHash)
    : null;
  const savedMeal = acquisition ? acquisition.savedMeal : await mealRepository.find(userId, mealId);
  if (savedMeal) {
    validateTextMealRetry({ record: savedMeal.record, userId, mealId });
    return completeMealRegistration({
      ...savedMeal,
      authRepository,
      clientId,
      clientSecret,
      healthClient,
      mealRepository,
    });
  }

  let analysis;
  try {
    analysis = await analyzeTextMeal({ client: analysisClient, text, inputAt });
  } catch (error) {
    if (acquisition) await mealRepository.releaseReservation(userId, mealId, requestHash);
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
    requestHash: acquisition ? requestHash : undefined,
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
    if (error instanceof AuthenticationError || error instanceof GoogleHealthPendingError ||
        error instanceof TextMealError || error instanceof MealRecordError) {
      response.status(error.status).json({ error: error.message });
      return;
    }
    response.status(500).json({ error: 'Meal could not be registered' });
  }
}

module.exports = { handleTextMealRequest };
