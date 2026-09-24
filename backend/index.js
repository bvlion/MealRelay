'use strict';

const functions = require('@google-cloud/functions-framework');
const { Firestore } = require('@google-cloud/firestore');
const OpenAI = require('openai');
const { initializeApp } = require('firebase-admin/app');
const { getMessaging } = require('firebase-admin/messaging');
const { completeAuthorization } = require('./src/auth');
const { loadAuthenticationConfig } = require('./src/config');
const { AuthenticationError } = require('./src/errors');
const { FirestoreAuthRepository } = require('./src/firestoreAuthRepository');
const { FirebaseInstallationRepository } = require('./src/firebaseInstallationRepository');
const { FirebaseMealNotifier } = require('./src/firebaseMealNotifier');
const { handleFirebaseInstallationRequest } = require('./src/firebaseInstallationEndpoint');
const { FirestoreMealRepository } = require('./src/firestoreMealRepository');
const { createGoogleAuthorizationService } = require('./src/googleOAuth');
const { handleImageMealRequest } = require('./src/imageMealEndpoint');
const { handleTextMealRequest } = require('./src/textMealEndpoint');

const firestore = new Firestore();
initializeApp();
const repository = new FirestoreAuthRepository(firestore);
const mealRepository = new FirestoreMealRepository(firestore);
const installationRepository = new FirebaseInstallationRepository(firestore);
const mealNotifier = new FirebaseMealNotifier({ installationRepository, messaging: getMessaging() });

functions.http('authExchange', async (request, response) => {
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
    const { allowedEmails, clientId, clientSecret } = loadAuthenticationConfig(process.env);
    const token = await completeAuthorization({
      code: request.body?.code,
      googleOAuth: createGoogleAuthorizationService(clientId, clientSecret),
      repository,
      allowedEmails,
    });
    response.status(200).json({ token });
  } catch (error) {
    if (error instanceof AuthenticationError) {
      response.status(error.status).json({ error: error.message });
      return;
    }
    response.status(500).json({ error: 'Authorization could not be completed' });
  }
});

functions.http('imageMeal', async (request, response) => {
  response.set('Cache-Control', 'no-store');
  const clientId = process.env.GOOGLE_OAUTH_CLIENT_ID;
  const clientSecret = process.env.GOOGLE_OAUTH_CLIENT_SECRET;
  const openaiApiKey = process.env.OPENAI_API_KEY;
  if (!openaiApiKey || !clientId || !clientSecret) {
    response.status(500).json({ error: 'Image analysis configuration is incomplete' });
    return;
  }
  const analysisClient = new OpenAI({ apiKey: openaiApiKey });
  await handleImageMealRequest({
    request,
    response,
    analysisClient,
    authRepository: repository,
    mealRepository,
    clientId,
    clientSecret,
    mealNotifier,
  });
});

functions.http('firebaseInstallation', async (request, response) => {
  await handleFirebaseInstallationRequest({
    request,
    response,
    authRepository: repository,
    installationRepository,
  });
});

functions.http('textMeal', async (request, response) => {
  response.set('Cache-Control', 'no-store');
  const clientId = process.env.GOOGLE_OAUTH_CLIENT_ID;
  const clientSecret = process.env.GOOGLE_OAUTH_CLIENT_SECRET;
  const openaiApiKey = process.env.OPENAI_API_KEY;
  if (!openaiApiKey || !clientId || !clientSecret) {
    response.status(500).json({ error: 'Text analysis configuration is incomplete' });
    return;
  }
  const analysisClient = new OpenAI({ apiKey: openaiApiKey });
  await handleTextMealRequest({
    request,
    response,
    analysisClient,
    authRepository: repository,
    mealRepository,
    clientId,
    clientSecret,
  });
});
