'use strict';

const { createHash } = require('node:crypto');
const { AuthenticationError } = require('./errors');
const { logBackendError } = require('./errorLogging');
const { authenticateMealRelayRequest } = require('./apiAuthentication');
const { analyzeMealImage } = require('./imageMealAnalysis');
const { ImageMealError } = require('./imageMealError');
const { parseImageMealRequest } = require('./imageMealRequest');
const { GoogleHealthPendingError } = require('./googleHealthNutrition');
const { MealRecordError, normalizeMealTime, validateImageMealRetry } = require('./mealRecord');
const { completeMealRegistration, registerMeal } = require('./registerMeal');

async function registerImageMeal({ authorization, userId, images, mealId, capturedAt,
  analysisClient, authRepository, mealRepository, clientId, clientSecret, healthClient, mealNotifier }) {
  if (typeof mealNotifier?.notifyRegistration !== 'function') {
    throw new Error('Meal registration notification is required');
  }
  normalizeMealTime(capturedAt);
  const requestHashBuilder = createHash('sha256');
  if (images.length === 1) {
    requestHashBuilder.update(images[0].data).update('\0').update(capturedAt);
  } else {
    images.forEach((image) => requestHashBuilder.update(image.mediaType).update('\0').update(image.data).update('\0'));
    requestHashBuilder.update(capturedAt);
  }
  const requestHash = requestHashBuilder.digest('hex');
  const reservation = await mealRepository.reserve(userId, mealId, requestHash);
  let result;
  if (reservation.type === 'saved') {
    validateImageMealRetry({ record: reservation.savedMeal.record, userId, mealId, capturedAt });
    result = await completeMealRegistration({
      ...reservation.savedMeal,
      authRepository,
      clientId,
      clientSecret,
      healthClient,
      mealRepository,
    });
  } else {
    if (reservation.type === 'different') throw new MealRecordError('Meal ID already has different content', 409);
    if (reservation.type === 'active') throw new MealRecordError('Meal is already being analyzed', 503);

    let analysis;
    try {
      analysis = await analyzeMealImage({ client: analysisClient, images });
    } catch (error) {
      await mealRepository.releaseReservation(reservation);
      throw error;
    }
    result = await registerMeal({
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
      reservation,
    });
  }
  if (!result.isAlreadyRegistered) {
    try {
      await mealNotifier.notifyRegistration(result.record);
    } catch (error) {
      logBackendError({
        message: 'Meal registration notification failed',
        error,
        severity: 'ERROR',
      });
      // Notification failure must not change the completed Google Health registration.
    }
  }
  return result;
}

async function handleImageMealRequest({ request, response, analysisClient, authRepository,
  mealRepository, clientId, clientSecret, healthClient, mealNotifier }) {
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
    const { images, mealId, capturedAt } = await parseImageMealRequest(request);
    const result = await registerImageMeal({
      authorization,
      userId,
      images,
      mealId,
      capturedAt,
      analysisClient,
      authRepository,
      mealRepository,
      clientId,
      clientSecret,
      healthClient,
      mealNotifier,
    });
    response.status(result.isAlreadyRegistered ? 200 : 201).json(result);
  } catch (error) {
    const isHandled = error instanceof AuthenticationError || error instanceof GoogleHealthPendingError ||
      error instanceof ImageMealError || error instanceof MealRecordError;
    const responseStatus = isHandled ? error.status : 500;
    logBackendError({
      message: 'Image meal request failed',
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

module.exports = { handleImageMealRequest };
