'use strict';

const { AuthenticationError } = require('./errors');
const { authenticateMealRelayRequest } = require('./apiAuthentication');
const { ImageMealError, analyzeMealImage, parseImageMealRequest } = require('./imageMeal');
const { GoogleHealthPendingError } = require('./googleHealthNutrition');
const { MealRecordError } = require('./mealRecord');
const { registerMeal } = require('./registerMeal');

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
    await authenticateMealRelayRequest({ authorization, repository: authRepository });
    const { image, mealId, capturedAt } = await parseImageMealRequest(request);
    const analysis = await analyzeMealImage({ client: analysisClient, image });
    const result = await registerMeal({
      route: 'image',
      authorization,
      mealId,
      occurredAt: capturedAt,
      analysis,
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
