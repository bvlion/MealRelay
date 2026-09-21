'use strict';

const { AuthenticationError } = require('./errors');
const { authenticateMealRelayRequest } = require('./apiAuthentication');
const { analyzeMealImage } = require('./imageMealAnalysis');
const { ImageMealError } = require('./imageMealError');
const { parseImageMealRequest } = require('./imageMealRequest');
const { GoogleHealthPendingError } = require('./googleHealthNutrition');
const { MealRecordError, validateImageMealRetry } = require('./mealRecord');
const { completeMealRegistration, registerMeal } = require('./registerMeal');

async function registerImageMeal({ authorization, userId, image, mealId, capturedAt,
  analysisClient, authRepository, mealRepository, clientId, clientSecret, healthClient }) {
  const savedMeal = await mealRepository.find(userId, mealId);
  if (savedMeal) {
    validateImageMealRetry({ record: savedMeal.record, userId, mealId, capturedAt });
    return completeMealRegistration({
      ...savedMeal,
      authRepository,
      clientId,
      clientSecret,
      healthClient,
      mealRepository,
    });
  }

  const analysis = await analyzeMealImage({ client: analysisClient, image });
  return registerMeal({
    route: 'image',
    authorization,
    authenticatedUserId: userId,
    mealId,
    occurredAt: capturedAt,
    analysis,
    authRepository,
    mealRepository,
    clientId,
    clientSecret,
    healthClient,
  });
}

async function handleImageMealRequest({ request, response, analysisClient, authRepository,
  mealRepository, clientId, clientSecret, healthClient }) {
  response.set('Cache-Control', 'no-store');
  if (request.method !== 'POST') {
    response.status(405).end();
    return;
  }
  if (!request.is('multipart/form-data')) {
    response.status(415).json({ error: 'Multipart form data is required' });
    return;
  }

  try {
    const authorization = request.get('authorization');
    const userId = await authenticateMealRelayRequest({ authorization, repository: authRepository });
    const { image, mealId, capturedAt } = await parseImageMealRequest(request);
    const result = await registerImageMeal({
      authorization,
      userId,
      image,
      mealId,
      capturedAt,
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
        error instanceof ImageMealError || error instanceof MealRecordError) {
      response.status(error.status).json({ error: error.message });
      return;
    }
    response.status(500).json({ error: 'Meal could not be registered' });
  }
}

module.exports = { handleImageMealRequest };
