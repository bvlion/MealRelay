'use strict';

const functions = require('@google-cloud/functions-framework');
const { Firestore } = require('@google-cloud/firestore');
const { completeAuthorization } = require('./src/auth');
const { loadAuthenticationConfig } = require('./src/config');
const { AuthenticationError } = require('./src/errors');
const { FirestoreAuthRepository } = require('./src/firestoreAuthRepository');
const { createGoogleAuthorizationService } = require('./src/googleOAuth');

const repository = new FirestoreAuthRepository(new Firestore());

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
